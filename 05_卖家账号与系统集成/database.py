"""共享数据结构和事务；业务 SQL 分别由第 2、3、4 个模块维护。"""
import os
import sqlite3
from contextlib import contextmanager
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
DB_PATH = Path(os.environ.get('SHOP_DB_PATH', str(ROOT / 'data' / 'shop.db')))


@contextmanager
def connection(write=False):
    """写操作先取得写锁，避免检查与修改之间发生并发竞争。"""
    conn = sqlite3.connect(DB_PATH, timeout=10)
    conn.row_factory = sqlite3.Row
    conn.execute('PRAGMA foreign_keys=ON')
    try:
        if write:
            conn.execute('BEGIN IMMEDIATE')
        yield conn
        conn.commit()
    except Exception:
        conn.rollback()
        raise
    finally:
        conn.close()


def initialize(password_hash):
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    with connection() as conn:
        conn.execute('PRAGMA journal_mode=WAL')
        conn.executescript('''
        CREATE TABLE IF NOT EXISTS seller (
            id INTEGER PRIMARY KEY CHECK(id=1),
            username TEXT NOT NULL UNIQUE,
            password_hash TEXT NOT NULL
        );
        CREATE TABLE IF NOT EXISTS sessions (
            token_hash TEXT PRIMARY KEY,
            expires_at REAL NOT NULL
        );
        CREATE TABLE IF NOT EXISTS products (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            name TEXT NOT NULL,
            description TEXT NOT NULL,
            image TEXT NOT NULL,
            price_cents INTEGER NOT NULL CHECK(price_cents>0),
            status TEXT NOT NULL CHECK(status IN ('active','frozen','sold')),
            selected_intent_id INTEGER REFERENCES intents(id),
            created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
            sold_at TEXT
        );
        CREATE UNIQUE INDEX IF NOT EXISTS one_unsold_product
            ON products((1)) WHERE status IN ('active','frozen');
        CREATE TABLE IF NOT EXISTS intents (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            product_id INTEGER NOT NULL REFERENCES products(id),
            buyer_name TEXT NOT NULL,
            contact TEXT NOT NULL,
            note TEXT NOT NULL,
            status TEXT NOT NULL DEFAULT 'pending'
                CHECK(status IN ('pending','selected','completed','closed')),
            created_at TEXT NOT NULL DEFAULT (datetime('now','localtime')),
            UNIQUE(product_id,contact)
        );
        CREATE TABLE IF NOT EXISTS trade_events (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            product_id INTEGER NOT NULL REFERENCES products(id),
            intent_id INTEGER NOT NULL REFERENCES intents(id),
            action TEXT NOT NULL CHECK(action IN ('freeze','success','failure')),
            created_at TEXT NOT NULL DEFAULT (datetime('now','localtime'))
        );
        ''')
        conn.execute('INSERT OR IGNORE INTO seller VALUES (1,?,?)',
                     ('seller', password_hash))


def public_product(row):
    """白名单输出，买家接口不暴露卖家选中的意向编号。"""
    if row is None:
        return None
    return {key: row[key] for key in
            ('id', 'name', 'description', 'image', 'price_cents', 'status', 'created_at')}
