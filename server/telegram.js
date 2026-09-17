const https = require('https');

/**
 * Sends a notification message to Telegram admin chat
 */
async function notifyAdminNewOrder(order) {
  const botToken = process.env.BOT_TOKEN;
  const adminChatId = process.env.ADMIN_CHAT_ID;

  if (!botToken || !adminChatId || botToken.includes('YOUR_TELEGRAM')) {
    console.log('[Telegram Bot] Notification skipped: BOT_TOKEN or ADMIN_CHAT_ID not configured');
    return false;
  }

  const tgUsername = order.telegram_username ? `@${order.telegram_username}` : '(Hin qabu)';
  const formattedPrice = Number(order.amount).toLocaleString();

  const text = 
`🛍️ UKUBII ORDER HAARAA

Order: #${order.order_number}
Maqaa: ${order.full_name || 'N/A'}
Bilbila: ${order.phone || 'N/A'}
Telegram: ${tgUsername}

Gatii: ${formattedPrice} Birr

Status: PENDING

Admin Panel keessatti nagahee ilaali.`;

  try {
    const payload = JSON.stringify({
      chat_id: adminChatId,
      text: text,
      parse_mode: 'HTML'
    });

    return new Promise((resolve) => {
      const options = {
        hostname: 'api.telegram.org',
        port: 443,
        path: `/bot${botToken}/sendMessage`,
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Content-Length': Buffer.byteLength(payload)
        },
        timeout: 8000
      };

      const req = https.request(options, (res) => {
        let body = '';
        res.on('data', chunk => body += chunk);
        res.on('end', () => {
          if (res.statusCode >= 200 && res.statusCode < 300) {
            console.log(`[Telegram Bot] Admin notification sent for Order #${order.order_number}`);
            resolve(true);
          } else {
            console.error(`[Telegram Bot] Send message failed: HTTP ${res.statusCode}`, body);
            resolve(false);
          }
        });
      });

      req.on('error', (err) => {
        console.error('[Telegram Bot] Notification request error:', err.message);
        resolve(false);
      });

      req.on('timeout', () => {
        req.destroy();
        console.error('[Telegram Bot] Notification request timeout');
        resolve(false);
      });

      req.write(payload);
      req.end();
    });
  } catch (err) {
    console.error('[Telegram Bot] Error sending admin notification:', err.message);
    return false;
  }
}

/**
 * Configure Telegram Bot menu button to point to WebApp
 */
async function setupBotMenuButton() {
  const botToken = process.env.BOT_TOKEN;
  const publicUrl = process.env.PUBLIC_URL;

  if (!botToken || !publicUrl || botToken.includes('YOUR_TELEGRAM')) {
    return;
  }

  try {
    const payload = JSON.stringify({
      menu_button: {
        type: 'web_app',
        text: '🛍️ Ukubii',
        web_app: { url: publicUrl }
      }
    });

    const req = https.request({
      hostname: 'api.telegram.org',
      port: 443,
      path: `/bot${botToken}/setChatMenuButton`,
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Content-Length': Buffer.byteLength(payload)
      },
      timeout: 5000
    }, (res) => {
      let body = '';
      res.on('data', d => body += d);
      res.on('end', () => {
        console.log('[Telegram Bot] SetChatMenuButton response:', body);
      });
    });

    req.on('error', (e) => console.error('[Telegram Bot] Menu button setup error:', e.message));
    req.write(payload);
    req.end();
  } catch (e) {
    console.error('[Telegram Bot] Setup error:', e.message);
  }
}

module.exports = {
  notifyAdminNewOrder,
  setupBotMenuButton
};
