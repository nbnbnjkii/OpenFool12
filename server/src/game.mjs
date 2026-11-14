import { randomInt, randomUUID } from 'node:crypto';
export function check(ok, message) { if (!ok) throw Object.assign(new Error(message), {status:400}); }
const rank=c=>+c.slice(0,-1)===1?14:+c.slice(0,-1);
const suit=c=>c.slice(-1);
export const beats=(card,target,trump)=>suit(card)===suit(target)?rank(card)>rank(target):suit(card)===trump;
export class Room {
  constructor(owner,opts={}) {
    this.id=randomUUID(); this.owner=owner.id; this.capacity=Number(opts.capacity??6);
    this.bots=opts.addBots===true?Number(opts.bots??1):0;
    check(Number.isInteger(this.capacity)&&this.capacity>=2&&this.capacity<=6,'Нужно 2–6 мест');
    check(Number.isInteger(this.bots)&&this.bots>=0&&this.bots<=this.capacity-2,'Оставьте минимум 2 места людям');
    this.deckSize=Number(opts.deckSize??36);check([24,36,52].includes(this.deckSize)&&this.deckSize>=this.capacity*6,'Колода слишком мала');
    this.pass=opts.pass===true;this.players=[];this.phase='waiting';this.updated=Date.now();this.version=0;this.add(owner);
  }
  touch() {this.updated=Date.now();this.version++;}
  add(user) {
    const existing=this.players.find(p=>p.id===user.id);if(existing)return;
    check(this.phase==='waiting','Партия уже началась');
    check(this.players.filter(p=>!p.bot).length<this.capacity-this.bots,'Нет свободных мест');
    this.players.push({id:user.id,name:user.nickname,avatar:user.avatar,bot:false,ready:false,hand:[],out:false,surrendered:false});this.touch();
  }
  seat(id) {const n=this.players.findIndex(p=>p.id===id);check(n>=0,'Вы не в комнате');return n;}
  next(from) {for(let n=1;n<=this.players.length;n++){let i=(from+n)%this.players.length;if(!this.players[i].out)return i;}return from;}
  throwers() {return Array.from({length:this.players.length},(_,n)=>(this.attacker+n)%this.players.length).filter(i=>i!==this.defender&&!this.players[i].out);}
  nextThrower(from) {return Array.from({length:this.players.length},(_,n)=>(from+n+1)%this.players.length).find(i=>this.throwers().includes(i)&&!this.done.has(i));}
  ready(id) {check(this.phase==='waiting','Партия уже началась');this.players[this.seat(id)].ready=true;this.touch();}
  start(id) {
    check(id===this.owner,'Начать может создатель');check(this.phase==='waiting','Партия уже идёт');
    check(this.players.length>=2,'Нужно минимум 2 живых игрока');check(this.players.every(p=>p.ready),'Не все готовы');
    for(let n=0;n<this.bots;n++)this.players.push({id:`bot:${randomUUID()}`,name:`Бот_${n+1}`,avatar:(n+1)%10,bot:true,ready:true,hand:[],out:false,surrendered:false});
    this.deck=[];const low=15-this.deckSize/4;
    for(let r=low;r<=14;r++)for(const s of 'cdhs')this.deck.push(`${r===14?1:r}${s}`);
    for(let i=this.deck.length-1;i>0;i--){const j=randomInt(i+1);[this.deck[i],this.deck[j]]=[this.deck[j],this.deck[i]];}
    this.trumpCard=this.deck[0];this.trump=suit(this.trumpCard);
    for(let k=0;k<6;k++)for(const p of this.players)p.hand.push(this.deck.pop());
    this.attacker=0;let lowest=15;
    this.players.forEach((p,i)=>p.hand.forEach(c=>{if(suit(c)===this.trump&&rank(c)<lowest){lowest=rank(c);this.attacker=i;}}));
    this.defender=this.next(this.attacker);this.table=[];this.discard=[];this.done=new Set();this.taking=false;
    this.limit=Math.min(6,this.players[this.defender].hand.length);this.phase='playing';this.turn=this.attacker;this.arm();this.touch();
  }
  arm() {this.deadline=Date.now()+30000;}
  action(id,action) {check(this.phase==='playing','Партия не идёт');const seat=this.seat(id);check(seat===this.turn,'Сейчас не ваш ход');this.apply(seat,action);this.touch();}
  apply(seat,a) {
    const p=this.players[seat];const card=a.card;const initial=this.table.length===0;
    if(a.type==='surrender') {this.surrender(p.id);return;}
    if(a.type==='take') {
      check(seat===this.defender&&!this.taking&&!initial,'Нельзя взять');
      this.taking=true;this.done.clear();this.turn=this.attacker;this.arm();return;
    }
    if(a.type==='done') {
      check(seat!==this.defender&&!initial&&(this.taking||this.table.every(x=>x.defense)),'Нельзя закончить');
      this.done.add(seat);const next=this.nextThrower(seat);
      if(next===undefined)this.finishRound();else {this.turn=next;this.arm();}return;
    }
    check(a.type==='play','Неизвестное действие');const at=p.hand.indexOf(card);check(at>=0,'Нет такой карты');
    if(seat===this.defender&&!this.taking) {
      if(a.pass===true) {
        check(this.pass&&this.table.every(x=>!x.defense)&&rank(card)===rank(this.table[0].attack),'Нельзя перевести');
        const next=this.next(this.defender);check(next!==this.defender&&this.players[next].hand.length>=this.table.length+1&&this.table.length<6,'Нельзя перевести');
        p.hand.splice(at,1);this.table.push({attack:card,defense:null});this.attacker=this.defender;this.defender=next;
        this.limit=Math.min(6,this.players[next].hand.length);this.done.clear();this.turn=next;
      } else {
        let target=Number(a.target);if(!Number.isInteger(target)||!this.table[target]||this.table[target].defense||!beats(card,this.table[target].attack,this.trump))target=this.table.findIndex(x=>!x.defense&&beats(card,x.attack,this.trump));
        check(target>=0,'Карта не бьёт');p.hand.splice(at,1);this.table[target].defense=card;this.done.clear();
        if(this.table.every(x=>x.defense)){this.turn=this.attacker;if(this.table.length>=this.limit)this.finishRound();}
      }
    } else {
      check(seat!==this.defender&&this.table.length<this.limit,'Нельзя подкинуть');
      check(initial||this.table.some(x=>rank(x.attack)===rank(card)||(x.defense&&rank(x.defense)===rank(card))),'Нет такого достоинства на столе');
      p.hand.splice(at,1);this.table.push({attack:card,defense:null});this.done.clear();
      this.turn=this.taking?seat:this.defender;
      if(this.taking&&this.table.length>=this.limit)this.finishRound();
    }
    this.arm();
  }
  finishRound() {
    const previousDefender=this.defender;const took=this.taking;
    const cards=this.table.flatMap(x=>[x.attack,...(x.defense?[x.defense]:[])]);
    if(took)this.players[this.defender].hand.push(...cards);else this.discard.push(...cards);
    const order=Array.from({length:this.players.length},(_,n)=>(this.attacker+n)%this.players.length).filter(i=>i!==this.defender).concat(this.defender);
    for(const i of order)if(!this.players[i].surrendered)while(this.players[i].hand.length<6&&this.deck.length)this.players[i].hand.push(this.deck.pop());
    this.players.forEach(p=>{if(!this.deck.length&&!p.hand.length)p.out=true;});
    this.table=[];
    if(this.players.filter(p=>!p.out).length<=1) {this.phase='ended';this.turn=-1;return;}
    this.attacker=took||this.players[previousDefender].out?this.next(previousDefender):previousDefender;
    this.defender=this.next(this.attacker);this.turn=this.attacker;this.table=[];this.taking=false;this.done.clear();
    this.limit=Math.min(6,this.players[this.defender].hand.length);this.arm();
  }
  surrender(id) {
    const p=this.players[this.seat(id)];if(p.surrendered)return;
    check(this.phase==='playing','Партия не идёт');
    p.surrendered=true;p.out=true;this.discard.push(...p.hand);p.hand=[];
    // Current round is settled safely before continuing with the remaining live seats.
    if(this.players.filter(p=>!p.out).length<=1){this.phase='ended';this.turn=-1;}
    else {
      this.discard.push(...this.table.flatMap(x=>[x.attack,...(x.defense?[x.defense]:[])]));this.table=[];
      if(this.players[this.attacker].out)this.attacker=this.next(this.attacker);
      this.defender=this.next(this.attacker);this.turn=this.attacker;this.done.clear();this.taking=false;
      this.limit=Math.min(6,this.players[this.defender].hand.length);this.arm();
    }
    this.touch();
  }
  leave(id) {
    const i=this.seat(id);
    if(this.phase==='playing')this.surrender(id);
    else {this.players.splice(i,1);if(this.owner===id)this.owner=this.players.find(p=>!p.bot)?.id??null;this.touch();}
  }
  botAction() {
    const p=this.players[this.turn];if(!p)return;
    const sorted=p.hand.slice().sort((a,b)=>(suit(a)===this.trump?100:0)+rank(a)-(suit(b)===this.trump?100:0)-rank(b));
    if(this.turn===this.defender&&!this.taking) {
      for(const c of sorted){const target=this.table.findIndex(x=>!x.defense&&beats(c,x.attack,this.trump));if(target>=0)return {type:'play',card:c,target};}
      return {type:'take'};
    }
    const card=sorted.find(c=>!this.table.length||this.table.some(x=>rank(x.attack)===rank(c)||(x.defense&&rank(x.defense)===rank(c))));
    return card&&this.table.length<this.limit?{type:'play',card}:{type:'done'};
  }
  tick(now=Date.now()) {
    if(this.phase!=='playing')return;
    const p=this.players[this.turn];if(!p)return;
    if((p.bot&&now>this.deadline-29200)||now>=this.deadline){this.apply(this.turn,this.botAction());this.touch();}
  }
  view(id) {
    const own=this.seat(id);
    return {id:this.id,code:this.code,owner:this.owner,capacity:this.capacity,bots:this.bots,pass:this.pass,
      phase:this.phase,version:this.version,seat:own,turn:this.turn??-1,attacker:this.attacker??-1,defender:this.defender??-1,
      deadline:this.deadline??0,serverTime:Date.now(),trump:this.trump??'',trumpCard:this.trumpCard??'',remaining:this.deck?.length??0,
      table:this.table??[],taking:this.taking??false,
      players:this.players.map((p,i)=>({id:p.id,name:p.name,avatar:p.avatar,bot:p.bot,ready:p.ready,out:p.out,surrendered:p.surrendered,count:p.hand.length,...(i===own?{hand:p.hand}:{})}))};
  }
}
