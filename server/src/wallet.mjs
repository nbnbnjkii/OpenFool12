import { check } from './game.mjs';

/** SQLite owns online balances. Never accept coin/crystal totals from a phone. */
export class Wallets {
  constructor(db) {
    this.db=db;
    db.exec(`CREATE TABLE IF NOT EXISTS wallets(user_id TEXT PRIMARY KEY REFERENCES users(id),coins INTEGER NOT NULL CHECK(coins>=0),crystals INTEGER NOT NULL CHECK(crystals>=0));
      CREATE TABLE IF NOT EXISTS wagers(room_id TEXT NOT NULL,user_id TEXT NOT NULL REFERENCES users(id),stake INTEGER NOT NULL,status TEXT NOT NULL DEFAULT 'open',PRIMARY KEY(room_id,user_id));
      INSERT OR IGNORE INTO wallets SELECT id,10000,20 FROM users;`);
    // Rooms are in memory: a process restart refunds ONLY unsettled escrows.
    this.transaction(()=>{
      for(const row of db.prepare("SELECT * FROM wagers WHERE status='open'").all()) {
        db.prepare('UPDATE wallets SET coins=coins+? WHERE user_id=?').run(row.stake,row.user_id);
        db.prepare("UPDATE wagers SET status='refunded' WHERE room_id=? AND user_id=?").run(row.room_id,row.user_id);
      }
    });
  }
  transaction(fn) {this.db.exec('BEGIN IMMEDIATE');try{const result=fn();this.db.exec('COMMIT');return result;}catch(e){this.db.exec('ROLLBACK');throw e;}}
  get(id) {this.db.prepare('INSERT OR IGNORE INTO wallets VALUES(?,10000,20)').run(id);return this.db.prepare('SELECT coins,crystals FROM wallets WHERE user_id=?').get(id);}
  reserve(room) {
    this.transaction(()=>{
      for(const p of room.players.filter(p=>!p.bot)) {
        check(this.get(p.id).coins>=room.stake,`Недостаточно монет у ${p.name}`);
        this.db.prepare('INSERT INTO wagers(room_id,user_id,stake) VALUES(?,?,?)').run(room.id,p.id,room.stake);
        this.db.prepare('UPDATE wallets SET coins=coins-? WHERE user_id=?').run(room.stake,p.id);
      }
    });
  }
  spend(id,cost) {
    check(Number.isInteger(cost)&&cost>0,'Неверная стоимость');
    const result=this.db.prepare('UPDATE wallets SET crystals=crystals-? WHERE user_id=? AND crystals>=?').run(cost,id,cost);
    check(result.changes===1,'Недостаточно кристаллов');
  }
  loss(room,id) {
    this.db.prepare("UPDATE wagers SET status='lost' WHERE room_id=? AND user_id=? AND status='open'").run(room.id,id);
  }
  settle(room) {
    if(room.phase!=='ended')return;
    const winners=room.players.filter(p=>!p.surrendered&&p.out);
    // A lone active survivor after all opponents surrendered is also a winner.
    if(!winners.length)winners.push(...room.players.filter(p=>!p.surrendered));
    this.transaction(()=>{
      const losers=room.players.length-winners.length;
      for(const p of room.players.filter(p=>!p.bot)) {
        const row=this.db.prepare("SELECT * FROM wagers WHERE room_id=? AND user_id=? AND status='open'").get(room.id,p.id);if(!row)continue;
        const won=winners.includes(p);
        if(won){const returned=room.stake+Math.floor(room.stake*losers/Math.max(1,winners.length));this.db.prepare('UPDATE wallets SET coins=coins+? WHERE user_id=?').run(returned,p.id);}
        this.db.prepare('UPDATE wagers SET status=? WHERE room_id=? AND user_id=?').run(won?'won':'lost',room.id,p.id);
      }
    });
  }
  refund(room) {
    this.transaction(()=>{
      for(const row of this.db.prepare("SELECT * FROM wagers WHERE room_id=? AND status='open'").all(room.id)) {
        this.db.prepare('UPDATE wallets SET coins=coins+? WHERE user_id=?').run(row.stake,row.user_id);
        this.db.prepare("UPDATE wagers SET status='refunded' WHERE room_id=? AND user_id=?").run(room.id,row.user_id);
      }
    });
  }
}
