import test from 'node:test';
import assert from 'node:assert/strict';
import { DatabaseSync } from 'node:sqlite';
import { Wallets } from '../src/wallet.mjs';
import { Room } from '../src/game.mjs';
function setup(count=2) {
 const db=new DatabaseSync(':memory:');db.exec('CREATE TABLE users(id TEXT PRIMARY KEY)');
 const users=Array.from({length:count},(_,i)=>({id:`u${i}`,nickname:`User ${i}`,avatar:0}));
 for(const u of users)db.prepare('INSERT INTO users VALUES(?)').run(u.id);
 const wallets=new Wallets(db);const room=new Room(users[0],{capacity:count,deckSize:36,stake:250});
 for(const u of users.slice(1))room.add(u);return {db,wallets,room,users};
}
test('waiting exits are free; insufficient stake rolls back every reservation',()=>{
 const {db,wallets,room}=setup();room.leave('u1');assert.equal(wallets.get('u1').coins,10000);
 room.add({id:'u1',nickname:'User 1',avatar:0});db.prepare('UPDATE wallets SET coins=10 WHERE user_id=?').run('u1');
 assert.throws(()=>wallets.reserve(room));assert.equal(wallets.get('u0').coins,10000);assert.equal(wallets.get('u1').coins,10);
 assert.equal(db.prepare('SELECT count(*) n FROM wagers').get().n,0);db.close();
});
test('restart refunds unsettled stakes but never a surrendered loss, and migration does not grant twice',()=>{
 const {db,wallets,room}=setup();wallets.reserve(room);wallets.loss(room,'u0');wallets.spend('u0',3);
 const recovered=new Wallets(db);assert.equal(recovered.get('u0').coins,9750);assert.equal(recovered.get('u1').coins,10000);assert.equal(recovered.get('u0').crystals,17);
 const again=new Wallets(db);assert.equal(again.get('u0').coins,9750);assert.equal(again.get('u1').coins,10000);db.close();
});
test('six live seats split lost stakes between winners, settlement is idempotent',()=>{
 const {db,wallets,room}=setup(6);wallets.reserve(room);
 room.players[0].surrendered=true;room.players[1].surrendered=true;
 for(const p of room.players.slice(2))p.out=true;
 room.phase='ended';wallets.loss(room,'u0');wallets.loss(room,'u1');wallets.settle(room);wallets.settle(room);
 for(const p of room.players)assert.equal(wallets.get(p.id).coins,p.surrendered?9750:10125);
 assert.equal(room.players.reduce((sum,p)=>sum+wallets.get(p.id).coins,0),60000);db.close();
});
test('expired undo and unaffordable tools never charge or alter the hand',()=>{
 const {db,wallets,room}=setup();for(const p of room.players)p.ready=true;wallets.reserve(room);room.start('u0');
 const actor=room.players[room.turn];const action=room.botAction();room.action(actor.id,action);const hand=actor.hand.slice();room.undo.until=0;
 assert.throws(()=>room.perk(actor.id,'undo',n=>wallets.spend(actor.id,n)));assert.equal(wallets.get(actor.id).crystals,20);assert.deepEqual(actor.hand,hand);
 db.prepare('UPDATE wallets SET crystals=0 WHERE user_id=?').run(actor.id);
 assert.throws(()=>room.perk(actor.id,'highlight',n=>wallets.spend(actor.id,n)));assert.equal(room.view(actor.id).perks.highlight,undefined);db.close();
});
test('a player already out without cards can leave without being reclassified as surrendered',()=>{
 const {db,wallets,room}=setup();for(const p of room.players)p.ready=true;wallets.reserve(room);room.start('u0');
 room.players[0].out=true;room.discard.push(...room.players[0].hand);room.players[0].hand=[];
 room.leave('u0');assert.equal(room.players[0].surrendered,false);room.phase='ended';wallets.settle(room);
 assert.equal(wallets.get('u0').coins,10250);assert.equal(wallets.get('u1').coins,9750);db.close();
});
