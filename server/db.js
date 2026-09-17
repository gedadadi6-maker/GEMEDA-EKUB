const path = require('path');
const fs = require('fs');
const Database = require('better-sqlite3');

const dataDir = path.join(__dirname, '..', 'data');
if (!fs.existsSync(dataDir)) {
  fs.mkdirSync(dataDir, { recursive: true });
}

const dbPath = path.join(dataDir, 'ukubii.db');
const db = new Database(dbPath);

// Enable WAL mode for high concurrency
db.pragma('journal_mode = WAL');
db.pragma('foreign_keys = ON');

// Initialize tables
db.exec(`
  CREATE TABLE IF NOT EXISTS settings (
    id INTEGER PRIMARY KEY,
    product_name TEXT NOT NULL,
    product_image TEXT,
    description TEXT,
    end_date TEXT,
    ticket_price INTEGER NOT NULL DEFAULT 3500,
    total_orders INTEGER NOT NULL DEFAULT 5000,
    admin_name TEXT NOT NULL DEFAULT 'Ukubii Admin',
    admin_account TEXT NOT NULL
  );

  CREATE TABLE IF NOT EXISTS orders (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    order_number INTEGER NOT NULL,
    telegram_id TEXT NOT NULL,
    telegram_username TEXT,
    full_name TEXT,
    phone TEXT,
    amount INTEGER NOT NULL,
    receipt_filename TEXT,
    status TEXT NOT NULL CHECK(status IN ('reserved', 'pending', 'confirmed', 'rejected', 'expired')),
    reservation_expires_at TEXT NOT NULL,
    created_at TEXT NOT NULL,
    updated_at TEXT NOT NULL
  );

  CREATE UNIQUE INDEX IF NOT EXISTS idx_active_order_number 
  ON orders(order_number) 
  WHERE status IN ('reserved', 'pending', 'confirmed');

  CREATE INDEX IF NOT EXISTS idx_orders_telegram_id ON orders(telegram_id);
  CREATE INDEX IF NOT EXISTS idx_orders_status ON orders(status);
`);

// Seed initial settings if empty
const settingsCount = db.prepare('SELECT COUNT(*) as count FROM settings').get();
if (settingsCount.count === 0) {
  const insertSettings = db.prepare(`
    INSERT INTO settings (
      id, product_name, product_image, description, end_date,
      ticket_price, total_orders, admin_name, admin_account
    ) VALUES (
      1,
      'Gemeda EV EQUB',
      '/assets/gemeda_ev.jpg',
      'Gemeda EV EQUB — Car Raffle & Equb tickets. Filadhu, reserve godhi, kaffaltii nagahee upload godhi.',
      '2026-10-31',
      3500,
      5000,
      'Ukubii Admin',
      'CBE (Commercial Bank of Ethiopia): 1000123456789 - Gemeda EV\\nTelebirr: 0911223344 (Gemeda EV)'
    )
  `);
  insertSettings.run();
}

function getSettings() {
  return db.prepare('SELECT * FROM settings WHERE id = 1').get();
}

function updateSettings(fields) {
  const current = getSettings();
  const updated = {
    product_name: fields.product_name !== undefined ? fields.product_name : current.product_name,
    product_image: fields.product_image !== undefined ? fields.product_image : current.product_image,
    description: fields.description !== undefined ? fields.description : current.description,
    end_date: fields.end_date !== undefined ? fields.end_date : current.end_date,
    ticket_price: fields.ticket_price !== undefined ? Number(fields.ticket_price) : current.ticket_price,
    total_orders: fields.total_orders !== undefined ? Number(fields.total_orders) : current.total_orders,
    admin_name: fields.admin_name !== undefined ? fields.admin_name : current.admin_name,
    admin_account: fields.admin_account !== undefined ? fields.admin_account : current.admin_account
  };

  db.prepare(`
    UPDATE settings SET
      product_name = ?,
      product_image = ?,
      description = ?,
      end_date = ?,
      ticket_price = ?,
      total_orders = ?,
      admin_name = ?,
      admin_account = ?
    WHERE id = 1
  `).run(
    updated.product_name,
    updated.product_image,
    updated.description,
    updated.end_date,
    updated.ticket_price,
    updated.total_orders,
    updated.admin_name,
    updated.admin_account
  );

  return getSettings();
}

// Cleanup expired reservations automatically
function cleanupExpiredReservations() {
  const now = new Date().toISOString();
  const info = db.prepare(`
    UPDATE orders 
    SET status = 'expired', updated_at = ? 
    WHERE status = 'reserved' AND reservation_expires_at < ?
  `).run(now, now);
  return info.changes;
}

// Get active order numbers map { "12": "reserved", "25": "pending", "100": "confirmed" }
function getActiveOrderNumbers() {
  cleanupExpiredReservations();
  const rows = db.prepare(`
    SELECT order_number, status 
    FROM orders 
    WHERE status IN ('reserved', 'pending', 'confirmed')
  `).all();
  
  const map = {};
  for (const row of rows) {
    map[row.order_number] = row.status;
  }
  return map;
}

// Reserve an order number atomically inside a transaction
function reserveOrderNumber({ orderNumber, telegramId, telegramUsername, minutes = 10 }) {
  cleanupExpiredReservations();

  const settings = getSettings();
  if (orderNumber < 1 || orderNumber > settings.total_orders) {
    throw { status: 400, message: `Order number 1 hanga ${settings.total_orders} gidduu ta'uu qaba.` };
  }

  const txn = db.transaction(() => {
    // Check if active order exists
    const existing = db.prepare(`
      SELECT id, status, telegram_id, reservation_expires_at 
      FROM orders 
      WHERE order_number = ? AND status IN ('reserved', 'pending', 'confirmed')
    `).get(orderNumber);

    const now = new Date();

    if (existing) {
      if (existing.status === 'reserved' && new Date(existing.reservation_expires_at) < now) {
        // Expire it now
        db.prepare(`UPDATE orders SET status = 'expired', updated_at = ? WHERE id = ?`)
          .run(now.toISOString(), existing.id);
      } else {
        throw { status: 409, message: 'Order number kun duraan qabameera.' };
      }
    }

    // Also cancel any previous 'reserved' order by the same user to avoid multiple active reservations
    db.prepare(`
      UPDATE orders 
      SET status = 'expired', updated_at = ? 
      WHERE telegram_id = ? AND status = 'reserved'
    `).run(now.toISOString(), telegramId);

    const expiresAt = new Date(now.getTime() + minutes * 60 * 1000).toISOString();
    const nowIso = now.toISOString();

    const insert = db.prepare(`
      INSERT INTO orders (
        order_number, telegram_id, telegram_username, amount, status,
        reservation_expires_at, created_at, updated_at
      ) VALUES (?, ?, ?, ?, 'reserved', ?, ?, ?)
    `).run(
      orderNumber,
      telegramId,
      telegramUsername || '',
      settings.ticket_price,
      expiresAt,
      nowIso,
      nowIso
    );

    return db.prepare('SELECT * FROM orders WHERE id = ?').get(insert.lastInsertRowid);
  });

  return txn();
}

// Submit payment / receipt for reserved order
function submitOrder({ orderId, orderNumber, telegramId, fullName, phone, receiptFilename }) {
  cleanupExpiredReservations();

  const now = new Date();
  const txn = db.transaction(() => {
    let order;
    if (orderId) {
      order = db.prepare('SELECT * FROM orders WHERE id = ?').get(orderId);
    } else if (orderNumber) {
      order = db.prepare(`
        SELECT * FROM orders 
        WHERE order_number = ? AND telegram_id = ? AND status = 'reserved'
        ORDER BY id DESC LIMIT 1
      `).get(orderNumber, telegramId);
    }

    if (!order) {
      throw { status: 404, message: 'Reservation order hin argamne.' };
    }

    if (order.telegram_id !== telegramId) {
      throw { status: 403, message: 'Order kana submit gochuuf hayyama hin qabdu.' };
    }

    if (order.status !== 'reserved') {
      throw { status: 400, message: `Order status '${order.status}' submit hin ta'u.` };
    }

    if (new Date(order.reservation_expires_at) < now) {
      db.prepare(`UPDATE orders SET status = 'expired', updated_at = ? WHERE id = ?`)
        .run(now.toISOString(), order.id);
      throw { status: 400, message: 'Yeroon reservation xumurameera. Mee lakkofsa biraa filadhu.' };
    }

    const nowIso = now.toISOString();
    db.prepare(`
      UPDATE orders SET 
        full_name = ?,
        phone = ?,
        receipt_filename = ?,
        status = 'pending',
        updated_at = ?
      WHERE id = ?
    `).run(fullName, phone, receiptFilename, nowIso, order.id);

    return db.prepare('SELECT * FROM orders WHERE id = ?').get(order.id);
  });

  return txn();
}

// Get user orders (up to 50)
function getOrdersByTelegramId(telegramId) {
  cleanupExpiredReservations();
  return db.prepare(`
    SELECT id, order_number, full_name, phone, amount, status, created_at, updated_at, reservation_expires_at
    FROM orders 
    WHERE telegram_id = ? 
    ORDER BY id DESC 
    LIMIT 50
  `).all(telegramId);
}

// Admin functions
function getAdminStats() {
  cleanupExpiredReservations();
  const settings = getSettings();
  const rows = db.prepare(`
    SELECT status, COUNT(*) as count 
    FROM orders 
    GROUP BY status
  `).all();

  const stats = {
    total_orders: 0,
    pending: 0,
    confirmed: 0,
    rejected: 0,
    reserved: 0,
    expired: 0,
    available_numbers: settings.total_orders
  };

  let activeCount = 0;
  for (const r of rows) {
    stats[r.status] = r.count;
    stats.total_orders += r.count;
    if (r.status === 'reserved' || r.status === 'pending' || r.status === 'confirmed') {
      activeCount += r.count;
    }
  }

  stats.available_numbers = Math.max(0, settings.total_orders - activeCount);
  return stats;
}

function getAdminOrders({ status, search, limit = 100, offset = 0 }) {
  cleanupExpiredReservations();
  let sql = 'SELECT * FROM orders WHERE 1=1';
  const params = [];

  if (status && status !== 'all') {
    sql += ' AND status = ?';
    params.push(status);
  }

  if (search && search.trim() !== '') {
    sql += ` AND (
      CAST(order_number AS TEXT) LIKE ? OR
      full_name LIKE ? OR
      phone LIKE ? OR
      telegram_username LIKE ? OR
      telegram_id LIKE ?
    )`;
    const q = `%${search.trim()}%`;
    params.push(q, q, q, q, q);
  }

  sql += ' ORDER BY id DESC LIMIT ? OFFSET ?';
  params.push(limit, offset);

  return db.prepare(sql).all(...params);
}

function getOrderById(id) {
  return db.prepare('SELECT * FROM orders WHERE id = ?').get(id);
}

function confirmOrder(id) {
  const order = getOrderById(id);
  if (!order) throw { status: 404, message: 'Order hin argamne.' };
  if (order.status !== 'pending') {
    throw { status: 400, message: `Status '${order.status}' gara 'confirmed' jijjiiramuu hin danda'u.` };
  }

  const nowIso = new Date().toISOString();
  db.prepare(`UPDATE orders SET status = 'confirmed', updated_at = ? WHERE id = ?`).run(nowIso, id);
  return getOrderById(id);
}

function rejectOrder(id, reason) {
  const order = getOrderById(id);
  if (!order) throw { status: 404, message: 'Order hin argamne.' };
  if (order.status !== 'pending') {
    throw { status: 400, message: `Status '${order.status}' gara 'rejected' jijjiiramuu hin danda'u.` };
  }

  const nowIso = new Date().toISOString();
  db.prepare(`UPDATE orders SET status = 'rejected', updated_at = ? WHERE id = ?`).run(nowIso, id);
  return getOrderById(id);
}

module.exports = {
  db,
  getSettings,
  updateSettings,
  cleanupExpiredReservations,
  getActiveOrderNumbers,
  reserveOrderNumber,
  submitOrder,
  getOrdersByTelegramId,
  getAdminStats,
  getAdminOrders,
  getOrderById,
  confirmOrder,
  rejectOrder
};
