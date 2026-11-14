import test from 'node:test';
import assert from 'node:assert/strict';
import { createService } from '../src/server.mjs';

test('register/login sessions, rooms, reciprocal friends and authenticated invites',async t=>{
 const service=createService({dbPath:':memory:'});await new Promise(resolve=>service.server.listen(0,'127.0.0.1',resolve));
 t.after(()=>service.close());const base=`http://127.0.0.1:${service.server.address().port}/v1`;
 async function request(path,body,token,method=body?'POST':'GET'){
  const response=await fetch(base+path,{method,headers:{'Content-Type':'application/json',...(token?{Authorization:`Bearer ${token}`}:{})},...(body?{body:JSON.stringify(body)}:{})});
  return {status:response.status,value:await response.json()};
 }
 const a=(await request('/auth/register',{login:'player_one',password:'long-pass-1'})).value;
 const b=(await request('/auth/register',{login:'player_two',password:'long-pass-2'})).value;
 assert.ok(a.token);assert.ok(b.token);assert.notEqual(a.token,b.token);
 assert.equal((await request('/auth/login',{login:'player_one',password:'wrong-pass'})).status,400);
 assert.equal((await request('/auth/login',{login:'player_one',password:'long-pass-1'})).status,200);
 assert.equal((await request('/me',null,a.token)).value.user.id,a.user.id);
 assert.equal((await request('/me')).status,401);
 const created=await request('/rooms',{capacity:6,deckSize:36},a.token);assert.equal(created.status,200);
 const code=created.value.room.code;assert.match(code,/^\d{4}$/);
 const joined=await request('/rooms/join',{code},b.token);assert.equal(joined.status,200);assert.equal(joined.value.room.players.length,2);
 assert.equal((await request('/rooms/start',{code},b.token)).status,400);
 assert.equal((await request('/friends/request',{code,playerId:b.user.id},a.token)).status,200);
 assert.equal((await request('/friends',null,b.token)).value.requests.length,1);
 assert.equal((await request('/friends/respond',{playerId:a.user.id,accept:true},b.token)).status,200);
 assert.equal((await request('/friends',null,a.token)).value.friends.length,1);
 await request('/rooms/leave',{code},b.token);
 assert.equal((await request('/invites',{code,playerId:b.user.id},a.token)).status,200);
 const invite=(await request('/invites',null,b.token)).value.invites[0];assert.equal(invite.code,code);
 assert.equal((await request('/invites/respond',{inviteId:invite.id,accept:true},b.token)).status,200);
 await request('/rooms/ready',{code},a.token);await request('/rooms/ready',{code},b.token);
 const started=await request('/rooms/start',{code},a.token);assert.equal(started.value.room.phase,'playing');
 assert.equal(started.value.room.players.filter(p=>'hand' in p).length,1);
 assert.equal((await request('/auth/logout',{},a.token)).status,200);
 assert.equal((await request('/me',null,a.token)).status,401);
 const hashes=service.db.prepare('SELECT password FROM users').all();assert.ok(hashes.every(x=>!x.password.includes('long-pass')));
 assert.ok(service.db.prepare('SELECT token FROM sessions').all().every(x=>x.token!==b.token));
});

test('database keeps accounts and saved sessions after service restart',async()=>{
 const {mkdtempSync,rmSync}=await import('node:fs');const {tmpdir}=await import('node:os');const {join}=await import('node:path');
 const folder=mkdtempSync(join(tmpdir(),'openfool-test-'));let service;
 try {
  const dbPath=join(folder,'accounts.sqlite');service=createService({dbPath});await new Promise(r=>service.server.listen(0,'127.0.0.1',r));
  let base=`http://127.0.0.1:${service.server.address().port}/v1`;
  const account=await (await fetch(base+'/auth/register',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({login:'persistent',password:'my-safe-test-password'})})).json();
  service.close();service=createService({dbPath});await new Promise(r=>service.server.listen(0,'127.0.0.1',r));
  base=`http://127.0.0.1:${service.server.address().port}/v1`;
  const me=await fetch(base+'/me',{headers:{Authorization:`Bearer ${account.token}`}});assert.equal(me.status,200);assert.equal((await me.json()).user.id,account.user.id);
 }finally{service?.close();rmSync(folder,{recursive:true,force:true});}
});
