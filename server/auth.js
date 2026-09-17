const crypto = require('crypto');

/**
 * Validates Telegram Mini App initData
 * https://core.telegram.org/bots/webapps#validating-data-received-via-the-web-app
 */
function verifyTelegramInitData(initDataString, botToken) {
  if (!initDataString || !botToken) {
    return { valid: false, error: 'initData ykn botToken hin jiru' };
  }

  try {
    const urlParams = new URLSearchParams(initDataString);
    const hash = urlParams.get('hash');
    if (!hash) {
      return { valid: false, error: 'hash hin argamne' };
    }

    urlParams.delete('hash');

    const params = [];
    for (const [key, value] of urlParams.entries()) {
      params.push(`${key}=${value}`);
    }
    params.sort();
    const dataCheckString = params.join('\n');

    // Secret key calculation: HMAC_SHA256(key="WebAppData", data=botToken)
    const secretKey = crypto
      .createHmac('sha256', 'WebAppData')
      .update(botToken)
      .digest();

    // Calculate hash: HMAC_SHA256(key=secretKey, data=dataCheckString)
    const calculatedHash = crypto
      .createHmac('sha256', secretKey)
      .update(dataCheckString)
      .digest('hex');

    if (calculatedHash !== hash) {
      return { valid: false, error: 'Hash mismatch' };
    }

    // Check auth_date (max age 1 hour = 3600 seconds)
    const authDate = parseInt(urlParams.get('auth_date') || '0', 10);
    const now = Math.floor(Date.now() / 1000);
    if (now - authDate > 3600) {
      return { valid: false, error: 'initData expired (1 hour exceeded)' };
    }

    // Parse user json
    const userJson = urlParams.get('user');
    let user = null;
    if (userJson) {
      try {
        user = JSON.parse(userJson);
      } catch (e) {
        // ignore parse error
      }
    }

    return {
      valid: true,
      user: user || { id: urlParams.get('id') || 'unknown' },
      authDate
    };
  } catch (err) {
    return { valid: false, error: err.message };
  }
}

/**
 * Express middleware for Customer routes
 */
function telegramAuthMiddleware(req, res, next) {
  const initData = req.headers['x-telegram-init-data'];
  const botToken = process.env.BOT_TOKEN;

  // Development / Demo preview fallback when opening outside Telegram
  const isDevOrDemo = process.env.NODE_ENV !== 'production' || !botToken || botToken.includes('YOUR_TELEGRAM');
  
  if (!initData) {
    // In standalone browser / demo mode, allow demo user header or graceful mock
    const demoUserHeader = req.headers['x-demo-user'];
    if (demoUserHeader) {
      try {
        req.telegramUser = JSON.parse(demoUserHeader);
        return next();
      } catch (e) {}
    }
    
    // In dev / demo preview, provide a simulated fallback user if requested
    if (isDevOrDemo) {
      req.telegramUser = {
        id: 'demo_user_1001',
        first_name: 'Gammadaa',
        last_name: 'Daadhii',
        username: 'gemeda_ev'
      };
      return next();
    }

    return res.status(401).json({ error: 'Telegram authentication barbaachisaadha.' });
  }

  const result = verifyTelegramInitData(initData, botToken);
  if (!result.valid) {
    // If dev or demo preview and verification failed due to dummy token, allow demo fallback
    if (isDevOrDemo) {
      try {
        const urlParams = new URLSearchParams(initData);
        const userJson = urlParams.get('user');
        if (userJson) {
          req.telegramUser = JSON.parse(userJson);
          return next();
        }
      } catch (e) {}
    }

    return res.status(401).json({ error: 'Telegram authentication kufe: ' + result.error });
  }

  req.telegramUser = result.user;
  next();
}

/**
 * Express middleware for Admin routes
 */
function adminAuthMiddleware(req, res, next) {
  const adminKey = req.headers['x-admin-key'];
  const expectedKey = process.env.ADMIN_KEY;

  if (!expectedKey) {
    return res.status(500).json({ error: 'Server ADMIN_KEY configure hin taane.' });
  }

  if (!adminKey || adminKey !== expectedKey) {
    return res.status(401).json({ error: 'Admin authentication failed.' });
  }

  next();
}

module.exports = {
  verifyTelegramInitData,
  telegramAuthMiddleware,
  adminAuthMiddleware
};
