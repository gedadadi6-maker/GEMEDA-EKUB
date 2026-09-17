require('dotenv').config({ override: true });
const express = require('express');
const helmet = require('helmet');
const cors = require('cors');
const path = require('path');
const fs = require('fs');
const crypto = require('crypto');
const multer = require('multer');

const {
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
} = require('./db');

const {
  telegramAuthMiddleware,
  adminAuthMiddleware
} = require('./auth');

const {
  notifyAdminNewOrder,
  setupBotMenuButton
} = require('./telegram');

const app = express();
const PORT = process.env.APP_PORT || 3000;

// Setup uploads directory
const uploadsDir = path.join(__dirname, '..', 'uploads');
if (!fs.existsSync(uploadsDir)) {
  fs.mkdirSync(uploadsDir, { recursive: true });
}

// Multer storage for secure receipt files
const storage = multer.diskStorage({
  destination: (req, file, cb) => {
    cb(null, uploadsDir);
  },
  filename: (req, file, cb) => {
    const ext = path.extname(file.originalname).toLowerCase();
    const randomName = crypto.randomBytes(16).toString('hex');
    cb(null, `${randomName}${ext}`);
  }
});

const allowedMimes = ['image/jpeg', 'image/png', 'image/webp', 'application/pdf'];
const upload = multer({
  storage: storage,
  limits: {
    fileSize: 5 * 1024 * 1024 // 5 MB max
  },
  fileFilter: (req, file, cb) => {
    if (allowedMimes.includes(file.mimetype)) {
      cb(null, true);
    } else {
      cb(new Error('Gosa file kana hin hayyamamu. JPG, PNG, WEBP, ykn PDF qofa upload godhaa.'));
    }
  }
});

// Security & Parsing Middlewares
app.use(helmet({
  contentSecurityPolicy: false // Allow Telegram WebApp script and inline styles for Mini App
}));
app.use(cors());
app.use(express.json());
app.use(express.urlencoded({ extended: true }));

// Serve frontend public static files (index.html, app.js, style.css, admin.html)
// Note: /uploads is deliberately NOT served statically
app.use(express.static(path.join(__dirname, '..', 'public')));

// Periodic cleanup of expired reservations every 30 seconds
setInterval(() => {
  try {
    const cleaned = cleanupExpiredReservations();
    if (cleaned > 0) {
      console.log(`[Ukubii Cleanup] Expired reservations cleared: ${cleaned}`);
    }
  } catch (err) {
    console.error('[Ukubii Cleanup Error]', err.message);
  }
}, 30000);

/* ========================================================================= */
/*                              PUBLIC & SHOP APIs                           */
/* ========================================================================= */

// GET /api/shop - Public shop information
app.get('/api/shop', (req, res) => {
  try {
    const settings = getSettings();
    res.json({
      product_name: settings.product_name,
      product_image: settings.product_image,
      description: settings.description,
      end_date: settings.end_date,
      ticket_price: settings.ticket_price,
      total_orders: settings.total_orders,
      admin_name: settings.admin_name,
      admin_account: settings.admin_account
    });
  } catch (err) {
    console.error('Error fetching shop data:', err);
    res.status(500).json({ error: 'Odeeffannoo shop argachuu hin dandeenye.' });
  }
});

// GET /api/orders/numbers - Active orders map
app.get('/api/orders/numbers', (req, res) => {
  try {
    const numbers = getActiveOrderNumbers();
    res.json(numbers);
  } catch (err) {
    console.error('Error fetching order numbers:', err);
    res.status(500).json({ error: 'Lakkoofsota order argachuu hin dandeenye.' });
  }
});

/* ========================================================================= */
/*                             CUSTOMER APIs                                 */
/* ========================================================================= */

// POST /api/orders/reserve - Reserve an order number
app.post('/api/orders/reserve', telegramAuthMiddleware, (req, res) => {
  try {
    const orderNumber = parseInt(req.body.order_number, 10);
    if (isNaN(orderNumber) || orderNumber < 1) {
      return res.status(400).json({ error: 'Lakkoofsa order sirrii galchaa.' });
    }

    const minutes = parseInt(process.env.RESERVATION_MINUTES, 10) || 10;
    const user = req.telegramUser || {};

    const reserved = reserveOrderNumber({
      orderNumber,
      telegramId: String(user.id || 'anonymous'),
      telegramUsername: user.username || '',
      minutes
    });

    res.status(201).json({
      success: true,
      data: reserved
    });
  } catch (err) {
    console.error('Reserve error:', err);
    const status = err.status || 500;
    res.status(status).json({ error: err.message || 'Reservation uumuun hin danda\'amne.' });
  }
});

// POST /api/orders - Submit order with receipt
app.post('/api/orders', telegramAuthMiddleware, (req, res) => {
  upload.single('receipt')(req, res, async (uploadErr) => {
    if (uploadErr) {
      return res.status(400).json({ error: uploadErr.message });
    }

    if (!req.file) {
      return res.status(400).json({ error: 'Nagahee kaffaltii (receipt) upload godhi.' });
    }

    const { full_name, phone, order_number, order_id } = req.body;

    if (!full_name || full_name.trim().length < 2 || full_name.trim().length > 100) {
      // Remove uploaded file on validation failure
      fs.unlink(req.file.path, () => {});
      return res.status(400).json({ error: 'Maqaa guutuu sirriitti guuti (2-100 characters).' });
    }

    if (!phone || phone.trim().length < 7 || phone.trim().length > 30) {
      fs.unlink(req.file.path, () => {});
      return res.status(400).json({ error: 'Lakkoofsa bilbilaa sirrii galchi (7-30 characters).' });
    }

    const user = req.telegramUser || {};

    try {
      const order = submitOrder({
        orderId: order_id ? parseInt(order_id, 10) : null,
        orderNumber: order_number ? parseInt(order_number, 10) : null,
        telegramId: String(user.id || 'anonymous'),
        fullName: full_name.trim(),
        phone: phone.trim(),
        receiptFilename: req.file.filename
      });

      // Send async admin Telegram notification (does not break user submission)
      notifyAdminNewOrder(order).catch(e => console.error('[Order Notification error]', e));

      res.status(200).json({
        success: true,
        data: order
      });
    } catch (err) {
      // Cleanup uploaded receipt if submit fails
      if (req.file && fs.existsSync(req.file.path)) {
        fs.unlink(req.file.path, () => {});
      }
      console.error('Submit order error:', err);
      const status = err.status || 500;
      res.status(status).json({ error: err.message || 'Order galmeessuun hin danda\'amne.' });
    }
  });
});

// GET /api/my-orders - Get logged-in customer's orders
app.get('/api/my-orders', telegramAuthMiddleware, (req, res) => {
  try {
    const user = req.telegramUser || {};
    const orders = getOrdersByTelegramId(String(user.id || 'anonymous'));
    res.json({
      success: true,
      data: orders
    });
  } catch (err) {
    console.error('My orders error:', err);
    res.status(500).json({ error: 'Orders kee argachuu hin dandeenye.' });
  }
});

/* ========================================================================= */
/*                              ADMIN APIs                                   */
/* ========================================================================= */

// GET /api/admin/stats - Dashboard statistics
app.get('/api/admin/stats', adminAuthMiddleware, (req, res) => {
  try {
    const stats = getAdminStats();
    res.json({
      success: true,
      data: stats
    });
  } catch (err) {
    console.error('Admin stats error:', err);
    res.status(500).json({ error: 'Statistics argachuu hin dandeenye.' });
  }
});

// GET /api/admin/orders - All orders with search & filter
app.get('/api/admin/orders', adminAuthMiddleware, (req, res) => {
  try {
    const { status, search, limit, offset } = req.query;
    const orders = getAdminOrders({
      status,
      search,
      limit: limit ? parseInt(limit, 10) : 100,
      offset: offset ? parseInt(offset, 10) : 0
    });
    res.json({
      success: true,
      data: orders
    });
  } catch (err) {
    console.error('Admin orders error:', err);
    res.status(500).json({ error: 'Order list argachuu hin dandeenye.' });
  }
});

// GET /api/admin/orders/:id - Single order details
app.get('/api/admin/orders/:id', adminAuthMiddleware, (req, res) => {
  try {
    const id = parseInt(req.params.id, 10);
    const order = getOrderById(id);
    if (!order) {
      return res.status(404).json({ error: 'Order hin argamne.' });
    }
    res.json({
      success: true,
      data: order
    });
  } catch (err) {
    console.error('Admin order by id error:', err);
    res.status(500).json({ error: 'Order argachuu hin dandeenye.' });
  }
});

// POST /api/admin/orders/:id/confirm - Confirm pending order
app.post('/api/admin/orders/:id/confirm', adminAuthMiddleware, (req, res) => {
  try {
    const id = parseInt(req.params.id, 10);
    const confirmed = confirmOrder(id);
    res.json({
      success: true,
      data: confirmed
    });
  } catch (err) {
    console.error('Confirm order error:', err);
    const status = err.status || 500;
    res.status(status).json({ error: err.message || 'Order confirm gochuun hin danda\'amne.' });
  }
});

// POST /api/admin/orders/:id/reject - Reject pending order
app.post('/api/admin/orders/:id/reject', adminAuthMiddleware, (req, res) => {
  try {
    const id = parseInt(req.params.id, 10);
    const reason = req.body.reason || '';
    const rejected = rejectOrder(id, reason);
    res.json({
      success: true,
      data: rejected
    });
  } catch (err) {
    console.error('Reject order error:', err);
    const status = err.status || 500;
    res.status(status).json({ error: err.message || 'Order reject gochuun hin danda\'amne.' });
  }
});

// GET /api/admin/receipts/:filename - Secure receipt download / preview
app.get('/api/admin/receipts/:filename', adminAuthMiddleware, (req, res) => {
  const filename = path.basename(req.params.filename); // Sanitize filename
  const filePath = path.join(uploadsDir, filename);

  if (!fs.existsSync(filePath)) {
    return res.status(404).json({ error: 'Nagaheen kaffaltii hin argamne.' });
  }

  const ext = path.extname(filename).toLowerCase();
  const mimeMap = {
    '.jpg': 'image/jpeg',
    '.jpeg': 'image/jpeg',
    '.png': 'image/png',
    '.webp': 'image/webp',
    '.pdf': 'application/pdf'
  };

  const contentType = mimeMap[ext] || 'application/octet-stream';
  res.setHeader('Content-Type', contentType);
  res.sendFile(filePath);
});

// PUT /api/admin/settings - Update shop settings
app.put('/api/admin/settings', adminAuthMiddleware, (req, res) => {
  try {
    const updated = updateSettings(req.body);
    res.json({
      success: true,
      data: updated
    });
  } catch (err) {
    console.error('Update settings error:', err);
    res.status(500).json({ error: 'Settings update gochuun hin danda\'amne.' });
  }
});

// Default fallback 404 handler
app.use((req, res) => {
  res.status(404).json({ error: 'Endpoint hin argamne.' });
});

// Start Server
app.listen(PORT, () => {
  console.log(`Ukubii server running on port ${PORT}`);
  // Set up bot menu button if configured
  setupBotMenuButton().catch(() => {});
});
