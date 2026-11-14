import http from 'node:http';
import { DatabaseSync } from 'node:sqlite';
import { randomBytes, randomInt, randomUUID, scrypt as scryptCallback, timingSafeEqual, createHash } from 'node:crypto';
import { promisify } from 'node:util';
import { mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { Room, check } from './game.mjs';
import { Wallets } from './wallet.mjs';
const scrypt=promisify(scryptCallback);
const digest=x=>createHash('sha256').update(x).digest('hex');
export function createService({dbPath=process.env.DATABASE_PATH||'data/openfool.sqlite',trustProxy=false}={}) {
  if(dbPath!==':memory:')mkdirSync(dirname(resolve(dbPath)),{recursive:true});
  const db=new DatabaseSync(dbPath);db.exec(`PRAGMA journal_mode=WAL; PRAGMA foreign_keys=ON;
    CREATE TABLE IF NOT EXISTS users(id TEXT PRIMARY KEY,login TEXT UNIQUE NOT NULL,password TEXT NOT NULL,salt TEXT NOT NULL,nickname TEXT NOT NULL,avatar INTEGER NOT NULL DEFAULT 0);
    CREATE TABLE IF NOT EXISTS sessions(token TEXT PRIMARY KEY,user_id TEXT NOT NULL REFERENCES users(id),expires INTEGER NOT NULL);
    CREATE TABLE IF NOT EXISTS friends(a TEXT NOT NULL REFERENCES users(id),b TEXT NOT NULL REFERENCES users(id),accepted INTEGER NOT NULL DEFAULT 0,PRIMARY KEY(a,b));`);
  const wallets=new Wallets(db);
  const rooms=new Map(),invites=new Map(),limits=new Map();let pendingHashes=0;
  const publicUser=u=>({id:u.id,login:u.login,nickname:u.nickname,avatar:u.avatar});
  const accountUser=u=>({...publicUser(u),wallet:wallets.get(u.id)});
  const user=id=>db.prepare('SELECT * FROM users WHERE id=?').get(id);
  const auth=req=>{
    const token=(req.headers.authorization||'').replace(/^Bearer /,'');
    const row=db.prepare('SELECT user_id FROM sessions WHERE token=? AND expires>?').get(digest(token),Date.now());
    if(!row)throw Object.assign(new Error('Войдите в аккаунт'),{status:401});return user(row.user_id);
  };
  const name=n=>String(n??'Игрок_1').replace(/[^А-Яа-яЁёa-zA-Z0-9 _-]/g,'').trim().replace(/ +/g,' ').slice(0,20)||'Игрок_1';
  const avatar=a=>Number.isInteger(a)&&a>=0&&a<=9?a:0;
  const rate=(key,max)=>{const now=Date.now();let bucket=limits.get(key);if(!bucket||now>bucket.until){bucket={n:0,until:now+60000};limits.set(key,bucket);}if(++bucket.n>max)throw Object.assign(new Error('Слишком много запросов. Подождите минуту'),{status:429});};
  const body=async req=>{let text='';for await(const chunk of req){text+=chunk;check(Buffer.byteLength(text)<=8192,'Слишком большой запрос');}try{const b=text?JSON.parse(text):{};check(b&&typeof b==='object'&&!Array.isArray(b),'Нужен JSON-объект');return b;}catch{check(false,'Неверный JSON');}};
  const activeRoom=id=>[...rooms.values()].find(r=>r.players.some(p=>p.id===id&&!p.surrendered)&&r.phase!=='ended');
  const syncRoom=r=>{for(const p of r.players)if(!p.bot&&p.surrendered)wallets.loss(r,p.id);if(!r.cancelled)wallets.settle(r);};
  const view=(r,id)=>{syncRoom(r);return {...r.view(id),wallet:wallets.get(id)};};
  const timer=setInterval(()=>{
    const now=Date.now();for(const [code,r] of rooms){try{r.tick(now);syncRoom(r);}catch(e){console.error('Room tick failed',r.id,e.message);r.cancelled=true;r.phase='ended';wallets.refund(r);}
      if(now-r.updated>2*60*60*1000||!r.players.some(p=>!p.bot)){wallets.refund(r);rooms.delete(code);}}
    for(const [k,v] of limits)if(v.until<now)limits.delete(k);
    for(const [k,v] of invites)if(v.expires<now)invites.delete(k);
    db.prepare('DELETE FROM sessions WHERE expires<?').run(now);
  },1000);timer.unref();
  const server=http.createServer(async(req,res)=>{
    res.setHeader('Content-Type','application/json; charset=utf-8');res.setHeader('Cache-Control','no-store');res.setHeader('X-Content-Type-Options','nosniff');
    try {
      const ip=trustProxy?String(req.headers['x-real-ip']||req.socket.remoteAddress):req.socket.remoteAddress;
      rate(`all:${ip}`,1200);const path=new URL(req.url,'http://local').pathname;const method=req.method;
      if(path==='/health'){res.end(JSON.stringify({ok:true,version:2}));return;}
      const b=['POST','PUT'].includes(method)?await body(req):{};let result;
      if(path==='/v1/auth/register'||path==='/v1/auth/login') {
        check(method==='POST','Нужен POST');rate(`auth:${ip}`,10);
        const login=String(b.login??'').toLowerCase();const password=String(b.password??'');
        check(/^[a-z0-9_]{3,24}$/.test(login),'Логин: 3–24 латинских буквы, цифры или _');
        check(password.length>=8&&password.length<=128,'Пароль: от 8 до 128 символов');
        check(pendingHashes<8,'Сервер занят');pendingHashes++;
        try {
          let u=db.prepare('SELECT * FROM users WHERE login=?').get(login);
          if(path.endsWith('/register')) {
            check(!u,'Логин уже занят');const salt=randomBytes(16).toString('hex');
            const hash=Buffer.from(await scrypt(password,salt,64)).toString('hex');
            try {db.prepare('INSERT INTO users(id,login,password,salt,nickname,avatar) VALUES(?,?,?,?,?,?)').run(randomUUID(),login,hash,salt,name(b.nickname||login),avatar(b.avatar));}
            catch(e){if(String(e).includes('UNIQUE'))check(false,'Логин уже занят');throw e;}
            u=db.prepare('SELECT * FROM users WHERE login=?').get(login);
          } else {
            const calculated=Buffer.from(await scrypt(password,u?.salt||'dummy-salt-for-missing-user',64));
            check(u&&timingSafeEqual(calculated,Buffer.from(u.password,'hex')),'Неверный логин или пароль');
          }
          const token=randomBytes(32).toString('base64url');const expires=Date.now()+30*24*60*60*1000;
          db.prepare('INSERT INTO sessions VALUES(?,?,?)').run(digest(token),u.id,expires);
          result={token,expires,user:accountUser(u),...(activeRoom(u.id)?{room:view(activeRoom(u.id),u.id)}:{})};
        }finally{pendingHashes--;}
      } else {
        const u=auth(req);rate(`user:${u.id}`,240);
        const room=()=>{const r=rooms.get(String(b.code??''));check(r,'Комната не найдена или закрыта');r.seat(u.id);return r;};
        if(path==='/v1/me'&&method==='GET')result={user:accountUser(u),...(activeRoom(u.id)?{room:view(activeRoom(u.id),u.id)}:{})};
        else if(path==='/v1/me'&&method==='PUT') {db.prepare('UPDATE users SET nickname=?,avatar=? WHERE id=?').run(name(b.nickname),avatar(b.avatar),u.id);result={user:accountUser(user(u.id))};}
        else if(path==='/v1/auth/logout'&&method==='POST'){db.prepare('DELETE FROM sessions WHERE token=?').run(digest((req.headers.authorization||'').replace(/^Bearer /,'')));result={ok:true};}
        else if(path==='/v1/rooms'&&method==='POST') {
          check(Number.isInteger(b.stake),'Обновите приложение: требуется выбор ставки');check(!activeRoom(u.id),'Вы уже в комнате');rate(`create:${u.id}`,10);const r=new Room(u,b);check(wallets.get(u.id).coins>=r.stake,'Ставка выше вашего баланса');
          check(rooms.size<9000,'Нет свободных кодов');do{r.code=String(randomInt(1000,10000));}while(rooms.has(r.code));rooms.set(r.code,r);result={room:view(r,u.id)};
        } else if(path==='/v1/rooms/join'&&method==='POST') {
          rate(`join:${u.id}`,20);const r=rooms.get(String(b.code??''));check(r,'Комната не найдена');const active=activeRoom(u.id);check(!active||active===r,'Вы уже в другой комнате');check(wallets.get(u.id).coins>=r.stake,'Недостаточно монет для ставки');r.add(u);result={room:view(r,u.id)};
        } else if(path==='/v1/rooms/state'&&method==='POST')result={room:view(room(),u.id)};
        else if(path==='/v1/rooms/ready'&&method==='POST'){check(b.economyVersion===1,'Обновите приложение для игры на ставку');const r=room();r.ready(u.id);result={room:view(r,u.id)};}
        else if(path==='/v1/rooms/start'&&method==='POST'){const r=room();check(r.phase==='waiting'&&r.owner===u.id,'Начать может создатель ожидающей комнаты');
          check(r.players.length>=2&&r.players.every(p=>p.ready),'Нужно минимум 2 готовых живых игрока');
          wallets.reserve(r);try{r.start(u.id);}catch(e){wallets.refund(r);r.cancelled=true;r.phase='ended';throw e;}result={room:view(r,u.id)};}
        else if(path==='/v1/rooms/action'&&method==='POST'){const r=room();r.action(u.id,b);result={room:view(r,u.id)};}
        else if(path==='/v1/rooms/leave'&&method==='POST'){const r=room();r.leave(u.id);syncRoom(r);result={ok:true,wallet:wallets.get(u.id)};}
        else if(path==='/v1/rooms/perk'&&method==='POST'){const r=room();r.perk(u.id,b.type,cost=>wallets.spend(u.id,cost));result={room:view(r,u.id)};}
        else if(path==='/v1/rooms/surrender'&&method==='POST'){const r=room();r.surrender(u.id);result={room:view(r,u.id)};}
        else if(path==='/v1/friends'&&method==='GET') {
          const rows=db.prepare('SELECT * FROM friends WHERE a=? OR b=?').all(u.id,u.id);
          result={friends:rows.filter(x=>x.accepted).map(x=>publicUser(user(x.a===u.id?x.b:x.a))),requests:rows.filter(x=>!x.accepted&&x.b===u.id).map(x=>publicUser(user(x.a))),sent:rows.filter(x=>!x.accepted&&x.a===u.id).map(x=>x.b)};
        } else if(path==='/v1/friends/request'&&method==='POST') {
          rate(`friend:${u.id}`,20);check(typeof b.playerId==='string'&&b.playerId!==u.id&&user(b.playerId),'Нет такого игрока');
          const r=room();check(r.players.some(p=>p.id===b.playerId&&!p.bot),'Игрок должен быть за вашим столом');
          const existing=db.prepare('SELECT * FROM friends WHERE (a=? AND b=?) OR (a=? AND b=?)').get(u.id,b.playerId,b.playerId,u.id);
          if(!existing)db.prepare('INSERT INTO friends(a,b) VALUES(?,?)').run(u.id,b.playerId);result={ok:true};
        } else if(path==='/v1/friends/respond'&&method==='POST') {
          const row=db.prepare('SELECT * FROM friends WHERE a=? AND b=? AND accepted=0').get(String(b.playerId),u.id);check(row,'Запрос не найден');
          if(b.accept===true)db.prepare('UPDATE friends SET accepted=1 WHERE a=? AND b=?').run(row.a,row.b);else db.prepare('DELETE FROM friends WHERE a=? AND b=?').run(row.a,row.b);result={ok:true};
        } else if(path==='/v1/invites'&&method==='GET')result={invites:[...invites.values()].filter(x=>x.to===u.id&&x.expires>Date.now()&&rooms.has(x.code)).map(x=>({id:x.id,code:x.code,from:publicUser(user(x.from))}))};
        else if(path==='/v1/invites'&&method==='POST') {
          rate(`invite:${u.id}`,20);const r=room();check(r.phase==='waiting','Партия уже началась');
          check(db.prepare('SELECT 1 FROM friends WHERE accepted=1 AND ((a=? AND b=?) OR (a=? AND b=?))').get(u.id,String(b.playerId),String(b.playerId),u.id),'Сначала добавьте игрока в друзья');
          const id=randomUUID();for(const[k,v]of invites)if(v.from===u.id&&v.to===b.playerId)invites.delete(k);
          invites.set(id,{id,from:u.id,to:b.playerId,code:r.code,expires:Date.now()+120000});result={ok:true};
        } else if(path==='/v1/invites/respond'&&method==='POST') {
          const invite=invites.get(String(b.inviteId));check(invite&&invite.to===u.id&&invite.expires>Date.now(),'Приглашение истекло');
          if(b.accept===true){const r=rooms.get(invite.code);check(r,'Комната закрыта');check(!activeRoom(u.id)||activeRoom(u.id)===r,'Вы уже в комнате');check(wallets.get(u.id).coins>=r.stake,'Недостаточно монет для ставки');r.add(u);result={room:view(r,u.id)};}else result={ok:true};invites.delete(invite.id);
        }else throw Object.assign(new Error('Метод не найден'),{status:404});
      }
      res.end(JSON.stringify(result));
    }catch(e){res.statusCode=e.status||500;res.end(JSON.stringify({error:res.statusCode===500?'Ошибка сервера':e.message}));if(res.statusCode===500)console.error(e);}
  });
  server.requestTimeout=15000;server.headersTimeout=10000;
  return {server,db,rooms,wallets,close:()=>{clearInterval(timer);server.close();db.close();}};
}
if(process.argv[1]&&resolve(process.argv[1])===fileURLToPath(import.meta.url)) {
  const service=createService({trustProxy:process.env.TRUST_PROXY==='1'});
  service.server.listen(Number(process.env.PORT||8080),process.env.HOST||'127.0.0.1',()=>console.log('OpenFool server ready'));
  for(const signal of ['SIGINT','SIGTERM'])process.on(signal,()=>{service.close();});
}
