// Telegram WebApp Setup
const tg = window.Telegram?.WebApp;
if (tg) {
  try {
    tg.ready();
    tg.expand();
  } catch (e) {
    console.warn('Telegram WebApp init warning:', e);
  }
}

// State
let shopData = null;
let activeNumbersMap = {};
let currentReservation = null;
let countdownInterval = null;
let currentRange = [1, 100];
let selectedNumber = null;

// DOM Elements
const numbersGrid = document.getElementById('numbersGrid');
const reservationBanner = document.getElementById('reservationBanner');
const timerDisplay = document.getElementById('timerDisplay');
const resOrderNumber = document.getElementById('resOrderNumber');
const resPriceDisplay = document.getElementById('resPriceDisplay');
const orderFormSection = document.getElementById('orderFormSection');
const submitOrderBtn = document.getElementById('submitOrderBtn');
const btnSubmitText = document.getElementById('btnSubmitText');
const successCard = document.getElementById('successCard');
const successOrderNumber = document.getElementById('successOrderNumber');
const userNameDisplay = document.getElementById('userNameDisplay');
const toastEl = document.getElementById('toast');
const filePreviewWrap = document.getElementById('filePreviewWrap');
const previewFileName = document.getElementById('previewFileName');
const receiptFileInput = document.getElementById('receiptFileInput');

// Initialize user display
const tgUser = tg?.initDataUnsafe?.user;
if (tgUser) {
  const displayName = [tgUser.first_name, tgUser.last_name].filter(Boolean).join(' ') || tgUser.username || 'Telegram User';
  userNameDisplay.textContent = displayName;
  const nameInput = document.getElementById('fullNameInput');
  if (nameInput && !nameInput.value) {
    nameInput.value = displayName;
  }
}

// Helper: Build API request headers
function getHeaders(isFormData = false) {
  const headers = {};
  if (!isFormData) {
    headers['Content-Type'] = 'application/json';
  }
  if (tg?.initData) {
    headers['X-Telegram-Init-Data'] = tg.initData;
  }
  return headers;
}

// Toast notification helper
function showToast(message, type = 'error') {
  toastEl.textContent = message;
  toastEl.className = `toast ${type}`;
  setTimeout(() => {
    toastEl.className = 'toast hidden';
  }, 4000);
}

// Tab navigation
function switchTab(tab) {
  document.getElementById('tabShopBtn').classList.toggle('active', tab === 'shop');
  document.getElementById('tabOrdersBtn').classList.toggle('active', tab === 'orders');
  document.getElementById('shopTab').classList.toggle('active', tab === 'shop');
  document.getElementById('ordersTab').classList.toggle('active', tab === 'orders');

  if (tab === 'orders') {
    loadMyOrders();
  }
}

// Load Shop details
async function loadShopData() {
  try {
    const res = await fetch('/api/shop');
    if (!res.ok) throw new Error('Odeeffannoo shop fiduun hin danda\'amne.');
    shopData = await res.json();

    document.getElementById('productTitle').textContent = shopData.product_name;
    document.getElementById('productDesc').textContent = shopData.description;
    document.getElementById('productPrice').textContent = `${Number(shopData.ticket_price).toLocaleString()} Birr`;
    document.getElementById('productEndDate').textContent = shopData.end_date || '2026-10-31';
    document.getElementById('totalOrdersLimit').textContent = shopData.total_orders;
    if (resPriceDisplay) {
      resPriceDisplay.textContent = `${Number(shopData.ticket_price).toLocaleString()} Birr`;
    }

    if (shopData.admin_account) {
      document.getElementById('adminAccountDetails').innerHTML = shopData.admin_account.replace(/\n/g, '<br>');
    }

    await loadOrderNumbers();
  } catch (err) {
    showToast(err.message || 'Internet connection kee ilaali. Mee irra deebi\'ii yaali.');
  }
}

// Load Active Order Numbers Map
async function loadOrderNumbers() {
  try {
    const res = await fetch('/api/orders/numbers');
    if (!res.ok) throw new Error('Lakkoofsota fiduun hin danda\'amne.');
    activeNumbersMap = await res.json();

    const total = shopData?.total_orders || 5000;
    const occupiedCount = Object.keys(activeNumbersMap).length;
    const available = Math.max(0, total - occupiedCount);
    document.getElementById('availableSlots').textContent = `${available.toLocaleString()} / ${total.toLocaleString()}`;

    renderNumbersGrid();
  } catch (err) {
    console.error(err);
    numbersGrid.innerHTML = '<div class="loading-spinner">Lakkoofsota fiduun hin danda\'amne.</div>';
  }
}

// Render grid for current range [start, end]
function renderNumbersGrid() {
  numbersGrid.innerHTML = '';
  const [start, end] = currentRange;
  const max = Math.min(end, shopData?.total_orders || 5000);

  const fragment = document.createDocumentFragment();

  for (let i = start; i <= max; i++) {
    const btn = document.createElement('button');
    btn.type = 'button';
    btn.className = 'num-btn';
    btn.textContent = i;
    btn.dataset.number = i;

    const status = activeNumbersMap[String(i)];

    if (currentReservation && currentReservation.order_number === i) {
      btn.classList.add('selected');
      const badge = document.createElement('span');
      badge.className = 'num-badge';
      badge.textContent = 'Qabatte';
      btn.appendChild(badge);
    } else if (status) {
      btn.classList.add(status);
      btn.disabled = true;
      const badge = document.createElement('span');
      badge.className = 'num-badge';
      badge.textContent = status;
      btn.appendChild(badge);
    } else {
      btn.onclick = () => selectAndReserveNumber(i);
    }

    fragment.appendChild(btn);
  }

  numbersGrid.appendChild(fragment);
}

// Change range from dropdown
function changeRange(rangeStr) {
  const parts = rangeStr.split('-').map(Number);
  if (parts.length === 2) {
    currentRange = [parts[0], parts[1]];
    renderNumbersGrid();
  }
}

// Quick jump to search number
function jumpToNumber() {
  const input = document.getElementById('searchNumberInput');
  const num = parseInt(input.value, 10);
  const max = shopData?.total_orders || 5000;

  if (isNaN(num) || num < 1 || num > max) {
    showToast(`Lakkoofsa 1 hanga ${max} gidduu galchi.`);
    return;
  }

  const rangeStart = Math.floor((num - 1) / 100) * 100 + 1;
  const rangeEnd = rangeStart + 99;
  currentRange = [rangeStart, rangeEnd];

  const rangeSelect = document.getElementById('rangeSelect');
  const targetOption = `${rangeStart}-${rangeEnd}`;
  if (rangeSelect.querySelector(`option[value="${targetOption}"]`)) {
    rangeSelect.value = targetOption;
  }

  renderNumbersGrid();

  setTimeout(() => {
    const el = document.querySelector(`.num-btn[data-number="${num}"]`);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'center' });
      el.classList.add('pulse');
      setTimeout(() => el.classList.remove('pulse'), 1000);
    }
  }, 100);
}

// Reserve an order number
async function selectAndReserveNumber(orderNumber) {
  try {
    selectedNumber = orderNumber;
    showToast(`Order #${orderNumber} reserve gochaa jira...`, 'success');

    const res = await fetch('/api/orders/reserve', {
      method: 'POST',
      headers: getHeaders(),
      body: JSON.stringify({ order_number: orderNumber })
    });

    const data = await res.json();
    if (!res.ok) {
      throw new Error(data.error || 'Reservation hin milkoofne.');
    }

    currentReservation = data.data;
    activeNumbersMap[String(orderNumber)] = 'reserved';

    // Show reservation banner
    resOrderNumber.textContent = currentReservation.order_number;
    reservationBanner.classList.remove('hidden');
    reservationBanner.scrollIntoView({ behavior: 'smooth', block: 'nearest' });

    // Enable order form
    orderFormSection.classList.remove('disabled');
    submitOrderBtn.disabled = false;
    successCard.classList.add('hidden');

    // Start timer
    startCountdown(currentReservation.reservation_expires_at);

    // Refresh grid
    renderNumbersGrid();
    showToast(`Order #${orderNumber} daqiiqaa 10f siif qabameera!`, 'success');
  } catch (err) {
    showToast(err.message);
    loadOrderNumbers();
  }
}

// Countdown timer
function startCountdown(expiresAtIso) {
  if (countdownInterval) clearInterval(countdownInterval);

  function update() {
    const now = new Date().getTime();
    const expireTime = new Date(expiresAtIso).getTime();
    const diff = expireTime - now;

    if (diff <= 0) {
      clearInterval(countdownInterval);
      timerDisplay.textContent = '00:00';
      handleReservationExpired();
      return;
    }

    const minutes = Math.floor(diff / 60000);
    const seconds = Math.floor((diff % 60000) / 1000);
    timerDisplay.textContent = `${String(minutes).padStart(2, '0')}:${String(seconds).padStart(2, '0')}`;
  }

  update();
  countdownInterval = setInterval(update, 1000);
}

function handleReservationExpired() {
  showToast('Yeroon reservation xumurameera. Mee lakkofsa biraa filadhu.');
  currentReservation = null;
  reservationBanner.classList.add('hidden');
  orderFormSection.classList.add('disabled');
  submitOrderBtn.disabled = true;
  loadOrderNumbers();
}

// Receipt file selection
function handleReceiptSelected(input) {
  const file = input.files[0];
  if (!file) return;

  const allowed = ['image/jpeg', 'image/png', 'image/webp', 'application/pdf'];
  if (!allowed.includes(file.type)) {
    showToast('Gosa file kana hin hayyamamu. JPG, PNG, WEBP, ykn PDF upload godhaa.');
    input.value = '';
    return;
  }

  if (file.size > 5 * 1024 * 1024) {
    showToast('Hangi file 5 MB ol ta\'uu hin danda\'u.');
    input.value = '';
    return;
  }

  previewFileName.textContent = `${file.name} (${(file.size / 1024).toFixed(0)} KB)`;
  filePreviewWrap.classList.remove('hidden');
  document.getElementById('uploadDummy').style.display = 'none';
}

function clearSelectedFile() {
  receiptFileInput.value = '';
  filePreviewWrap.classList.add('hidden');
  document.getElementById('uploadDummy').style.display = 'flex';
}

// Submit Order
async function handleOrderSubmit(event) {
  event.preventDefault();

  if (!currentReservation) {
    showToast('Mee duraan order number filadhu reserve godhi.');
    return;
  }

  const fullName = document.getElementById('fullNameInput').value.trim();
  const phone = document.getElementById('phoneInput').value.trim();
  const file = receiptFileInput.files[0];

  if (!fullName || fullName.length < 2 || fullName.length > 100) {
    showToast('Maqaa guutuu sirriitti guuti (2-100 characters).');
    return;
  }

  if (!phone || phone.length < 7 || phone.length > 30) {
    showToast('Lakkoofsa bilbilaa sirrii galchi (7-30 characters).');
    return;
  }

  if (!file) {
    showToast('Nagahee kaffaltii (receipt) upload godhi.');
    return;
  }

  // Prevent double click
  submitOrderBtn.disabled = true;
  btnSubmitText.textContent = 'Galmeessaa jira...';

  try {
    const formData = new FormData();
    formData.append('order_id', currentReservation.id);
    formData.append('order_number', currentReservation.order_number);
    formData.append('full_name', fullName);
    formData.append('phone', phone);
    formData.append('receipt', file);

    const res = await fetch('/api/orders', {
      method: 'POST',
      headers: getHeaders(true),
      body: formData
    });

    const data = await res.json();
    if (!res.ok) {
      throw new Error(data.error || 'Order submit gochuun hin danda\'amne.');
    }

    // Success!
    if (countdownInterval) clearInterval(countdownInterval);
    reservationBanner.classList.add('hidden');
    orderFormSection.classList.add('disabled');
    clearSelectedFile();

    successOrderNumber.textContent = currentReservation.order_number;
    successCard.classList.remove('hidden');
    successCard.scrollIntoView({ behavior: 'smooth' });

    currentReservation = null;
    showToast('✅ Order kee milkaa\'inaan galmaa\'eera!', 'success');

    await loadOrderNumbers();
    loadMyOrders();
  } catch (err) {
    showToast(err.message);
  } finally {
    submitOrderBtn.disabled = false;
    btnSubmitText.textContent = 'ORDER SUBMIT GODHI';
  }
}

// Load My Orders
async function loadMyOrders() {
  const container = document.getElementById('myOrdersList');
  container.innerHTML = '<div class="loading-spinner">Orders fe\'aa jira...</div>';

  try {
    const res = await fetch('/api/my-orders', {
      headers: getHeaders()
    });

    const data = await res.json();
    if (!res.ok) throw new Error(data.error || 'Orders fiduun hin danda\'amne.');

    const orders = data.data || [];
    const badge = document.getElementById('orderCountBadge');
    if (orders.length > 0) {
      badge.textContent = orders.length;
      badge.style.display = 'inline-block';
    } else {
      badge.style.display = 'none';
    }

    if (orders.length === 0) {
      container.innerHTML = `
        <div style="text-align:center; padding: 30px; color: var(--hint-color);">
          <div style="font-size: 32px; margin-bottom: 8px;">📭</div>
          <p>Hamma ammaatti order homaa hin qabdu.</p>
        </div>
      `;
      return;
    }

    container.innerHTML = orders.map(o => `
      <div class="order-card">
        <div class="order-card-header">
          <span class="order-num">#${o.order_number}</span>
          <span class="status-badge ${o.status}">${o.status}</span>
        </div>
        <div class="order-card-body">
          <div><b>Maqaa:</b> ${o.full_name || 'N/A'}</div>
          <div><b>Bilbila:</b> ${o.phone || 'N/A'}</div>
          <div><b>Gatii:</b> ${Number(o.amount).toLocaleString()} Birr</div>
          <div><b>Guyyaa:</b> ${new Date(o.created_at).toLocaleDateString()} ${new Date(o.created_at).toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })}</div>
        </div>
      </div>
    `).join('');
  } catch (err) {
    container.innerHTML = `<div style="color:var(--danger-color); text-align:center; padding:20px;">${err.message}</div>`;
  }
}

// On document load
document.addEventListener('DOMContentLoaded', () => {
  loadShopData();
});
