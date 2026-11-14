import test from 'node:test';
import assert from 'node:assert/strict';
import { Room } from '../src/game.mjs';
const user=i=>({id:`user-${i}`,nickname:`Игрок ${i}`,avatar:i%10});
function game(count,bots=0){const r=new Room(user(0),{capacity:count,deckSize:count<=4?24:36,addBots:bots>0,bots});for(let i=1;i<count-bots;i++)r.add(user(i));for(const p of r.players)r.ready(p.id);r.start(user(0).id);return r;}
test('2–6 live players, no bots by default; private hands and card conservation',()=>{
 for(let n=2;n<=6;n++){
  const r=game(n);assert.equal(r.players.length,n);assert.equal(r.players.filter(p=>p.bot).length,0);
  for(const p of r.players){const view=r.view(p.id);assert.equal(view.players.filter(p=>'hand' in p).length,1);assert.equal(view.players[view.seat].hand.length,6);}
  const cards=r.players.flatMap(p=>p.hand).concat(r.deck);assert.equal(cards.length,r.deckSize);assert.equal(new Set(cards).size,r.deckSize);
 }
});
test('bots only added explicitly and two human seats are mandatory',()=>{
 const r=game(6,4);assert.equal(r.players.filter(p=>p.bot).length,4);assert.equal(r.players.filter(p=>!p.bot).length,2);
 assert.throws(()=>new Room(user(0),{capacity:6,addBots:true,bots:5}));
 const no=new Room(user(0),{capacity:6,bots:4});assert.equal(no.bots,0);
 assert.throws(()=>no.start(user(0).id));
});
test('out-of-turn actions and joining started rooms are rejected',()=>{
 const r=game(6);const wrong=r.players.find((_,i)=>i!==r.turn);
 const before=JSON.stringify(r.view(r.players[0].id));assert.throws(()=>r.action(wrong.id,{type:'play',card:wrong.hand[0]}));
 assert.equal(JSON.stringify(r.view(r.players[0].id)).replace(/"serverTime":\d+/,''),before.replace(/"serverTime":\d+/,''));
 assert.throws(()=>r.add(user(8)));
});
test('automated legal games finish for every count and conserve all cards',()=>{
 for(let count=2;count<=6;count++)for(let trial=0;trial<8;trial++){
  const r=game(count);let moves=0;
  while(r.phase==='playing'&&moves<20000){const action=r.botAction();r.action(r.players[r.turn].id,action);moves++;
   const cards=r.deck.concat(r.discard,r.players.flatMap(p=>p.hand),r.table.flatMap(p=>[p.attack,...(p.defense?[p.defense]:[])]));
   assert.equal(cards.length,r.deckSize);assert.equal(new Set(cards).size,r.deckSize);
  }
  assert.equal(r.phase,'ended',`count ${count}, moves ${moves}`);
 }
});
test('surrender is idempotent, never reveals other hands, and remaining players can continue',()=>{
 const r=game(6);r.surrender('user-0');r.surrender('user-0');assert.equal(r.players[0].surrendered,true);assert.equal(r.players[0].hand.length,0);
 assert.equal(r.view('user-1').players[2].hand,undefined);
 let moves=0;while(r.phase==='playing'&&moves++<20000)r.action(r.players[r.turn].id,r.botAction());assert.equal(r.phase,'ended');
});
