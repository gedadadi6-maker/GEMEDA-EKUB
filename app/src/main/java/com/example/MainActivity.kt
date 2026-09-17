package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

// Data Models
data class UkubiiOrder(
    val id: Int,
    val orderNumber: Int,
    val telegramId: String,
    val telegramUsername: String,
    val fullName: String,
    val phone: String,
    val amount: Int,
    val receiptFilename: String,
    val status: String, // "reserved", "pending", "confirmed", "rejected", "expired"
    val reservationExpiresAt: Long,
    val createdAt: String
)

data class UkubiiSettings(
    val productName: String = "Gemeda EV EQUB",
    val ticketPrice: Int = 3500,
    val totalOrders: Int = 5000,
    val endDate: String = "2026-10-31",
    val description: String = "Gemeda EV EQUB — Car Raffle & Equb tickets. Filadhu, reserve godhi, kaffaltii nagahee upload godhi.",
    val adminName: String = "Ukubii Admin",
    val adminAccount: String = "CBE (Commercial Bank of Ethiopia): 1000123456789 (Gemeda EV)\nTelebirr: 0911223344 (Gemeda EV)"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                UkubiiApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UkubiiApp() {
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Shop, 1: My Orders, 2: Admin
    var settings by remember { mutableStateOf(UkubiiSettings()) }
    
    // Sample / Live Orders in State
    val orders = remember {
        mutableStateListOf(
            UkubiiOrder(
                id = 1,
                orderNumber = 12,
                telegramId = "tg_1001",
                telegramUsername = "tolera_b",
                fullName = "Toleraa Baqqalaa",
                phone = "0911223344",
                amount = 3500,
                receiptFilename = "cbe_receipt_12.jpg",
                status = "confirmed",
                reservationExpiresAt = 0L,
                createdAt = "2026-09-15 10:30"
            ),
            UkubiiOrder(
                id = 2,
                orderNumber = 25,
                telegramId = "tg_1002",
                telegramUsername = "chala_k",
                fullName = "Caalaa Kabbadaa",
                phone = "0922334455",
                amount = 3500,
                receiptFilename = "telebirr_receipt_25.png",
                status = "pending",
                reservationExpiresAt = 0L,
                createdAt = "2026-09-16 09:15"
            ),
            UkubiiOrder(
                id = 3,
                orderNumber = 100,
                telegramId = "tg_1003",
                telegramUsername = "marta_d",
                fullName = "Maartaa Dhaabaa",
                phone = "0933445566",
                amount = 3500,
                receiptFilename = "receipt_100.pdf",
                status = "confirmed",
                reservationExpiresAt = 0L,
                createdAt = "2026-09-16 11:00"
            )
        )
    }

    var activeReservation by remember { mutableStateOf<UkubiiOrder?>(null) }
    var countdownSeconds by remember { mutableIntStateOf(600) } // 10 minutes default
    var showSuccessDialog by remember { mutableStateOf<UkubiiOrder?>(null) }
    var snackbarMessage by remember { mutableStateOf<String?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }

    // Countdown timer loop
    LaunchedEffect(activeReservation) {
        if (activeReservation != null) {
            countdownSeconds = 600
            while (countdownSeconds > 0 && activeReservation != null) {
                delay(1000)
                countdownSeconds -= 1
            }
            if (countdownSeconds <= 0 && activeReservation != null) {
                // Reservation expired
                val expiredOrder = activeReservation!!
                orders.removeAll { it.id == expiredOrder.id }
                orders.add(expiredOrder.copy(status = "expired"))
                activeReservation = null
                snackbarMessage = "Yeroon reservation xumurameera. Mee lakkofsa biraa filadhu."
            }
        }
    }

    LaunchedEffect(snackbarMessage) {
        snackbarMessage?.let {
            snackbarHostState.showSnackbar(it)
            snackbarMessage = null
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = UkubiiDarkBg,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = UkubiiPrimary,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("🛍️", fontSize = 18.sp)
                            }
                        }
                        Column {
                            Text(
                                "UKUBII",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color.White
                            )
                            Text(
                                "Telegram Mini App",
                                fontSize = 11.sp,
                                color = UkubiiTextMuted
                            )
                        }
                    }
                },
                actions = {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = UkubiiCardBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, UkubiiCardBorder)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(UkubiiGreen, CircleShape)
                            )
                            Text(
                                "@gemeda_ev",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = UkubiiTextPrimary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = UkubiiDarkBg
                )
            )
        },
        bottomBar = {
            NavigationBar(
                containerColor = UkubiiCardBg,
                tonalElevation = 8.dp
            ) {
                NavigationBarItem(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    icon = { Icon(Icons.Default.ShoppingCart, contentDescription = "Shop") },
                    label = { Text("Shop", fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = UkubiiPrimary,
                        indicatorColor = UkubiiPrimary,
                        unselectedIconColor = UkubiiTextMuted,
                        unselectedTextColor = UkubiiTextMuted
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    icon = {
                        BadgedBox(
                            badge = {
                                val count = orders.count { it.telegramId == "current_user" }
                                if (count > 0) {
                                    Badge(containerColor = UkubiiGold) { Text("$count") }
                                }
                            }
                        ) {
                            Icon(Icons.Default.ReceiptLong, contentDescription = "My Orders")
                        }
                    },
                    label = { Text("Order Koo", fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = UkubiiPrimary,
                        indicatorColor = UkubiiPrimary,
                        unselectedIconColor = UkubiiTextMuted,
                        unselectedTextColor = UkubiiTextMuted
                    )
                )
                NavigationBarItem(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    icon = { Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin") },
                    label = { Text("Admin", fontWeight = FontWeight.Bold) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = Color.White,
                        selectedTextColor = UkubiiPrimary,
                        indicatorColor = UkubiiPrimary,
                        unselectedIconColor = UkubiiTextMuted,
                        unselectedTextColor = UkubiiTextMuted
                    )
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (selectedTab) {
                0 -> CustomerShopScreen(
                    settings = settings,
                    orders = orders,
                    activeReservation = activeReservation,
                    countdownSeconds = countdownSeconds,
                    onReserve = { number ->
                        // Check if already taken
                        val existing = orders.find { it.orderNumber == number && it.status in listOf("reserved", "pending", "confirmed") }
                        if (existing != null) {
                            snackbarMessage = "Order number kun duraan qabameera."
                        } else {
                            val newOrder = UkubiiOrder(
                                id = (orders.maxOfOrNull { it.id } ?: 0) + 1,
                                orderNumber = number,
                                telegramId = "current_user",
                                telegramUsername = "gemeda_ev",
                                fullName = "",
                                phone = "",
                                amount = settings.ticketPrice,
                                receiptFilename = "",
                                status = "reserved",
                                reservationExpiresAt = System.currentTimeMillis() + 600000L,
                                createdAt = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date())
                            )
                            orders.removeAll { it.telegramId == "current_user" && it.status == "reserved" }
                            orders.add(newOrder)
                            activeReservation = newOrder
                            snackbarMessage = "Order #$number daqiiqaa 10f siif qabameera!"
                        }
                    },
                    onSubmitOrder = { fullName, phone ->
                        val current = activeReservation
                        if (current != null) {
                            val index = orders.indexOfFirst { it.id == current.id }
                            val updated = current.copy(
                                fullName = fullName,
                                phone = phone,
                                receiptFilename = "receipt_${current.orderNumber}.jpg",
                                status = "pending"
                            )
                            if (index != -1) {
                                orders[index] = updated
                            } else {
                                orders.add(updated)
                            }
                            activeReservation = null
                            showSuccessDialog = updated
                        }
                    },
                    onCancelReservation = {
                        activeReservation?.let {
                            orders.removeAll { o -> o.id == it.id }
                        }
                        activeReservation = null
                        snackbarMessage = "Reservation haqameera."
                    }
                )
                1 -> CustomerOrdersScreen(
                    orders = orders.filter { it.telegramId == "current_user" || it.id in listOf(1, 2, 3) }
                )
                2 -> AdminScreen(
                    settings = settings,
                    orders = orders,
                    onConfirmOrder = { orderId ->
                        val idx = orders.indexOfFirst { it.id == orderId }
                        if (idx != -1) {
                            orders[idx] = orders[idx].copy(status = "confirmed")
                            snackbarMessage = "Order #${orders[idx].orderNumber} CONFIRMED ta'eera!"
                        }
                    },
                    onRejectOrder = { orderId ->
                        val idx = orders.indexOfFirst { it.id == orderId }
                        if (idx != -1) {
                            orders[idx] = orders[idx].copy(status = "rejected")
                            snackbarMessage = "Order #${orders[idx].orderNumber} REJECTED ta'eera!"
                        }
                    },
                    onUpdateSettings = { newSettings ->
                        settings = newSettings
                        snackbarMessage = "Settings milkaa'inaan save ta'eera!"
                    }
                )
            }
        }

        // Success Dialog
        showSuccessDialog?.let { order ->
            AlertDialog(
                onDismissRequest = { showSuccessDialog = null },
                containerColor = UkubiiCardBg,
                title = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("✅", fontSize = 40.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Order Kee Milkaa'inaan Galmaa'eera!",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                text = {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = UkubiiDarkBg,
                            modifier = Modifier.padding(vertical = 8.dp)
                        ) {
                            Text(
                                "Order #${order.orderNumber}",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = UkubiiGold,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = Color(0xFF6366F1).copy(alpha = 0.3f),
                            modifier = Modifier.padding(bottom = 12.dp)
                        ) {
                            Text(
                                "STATUS: PENDING",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF818CF8),
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Text(
                            "Adminiin kaffaltii kee nagahee irraa erga mirkaneesse booda status kee CONFIRMED ta'a.",
                            fontSize = 13.sp,
                            color = UkubiiTextMuted,
                            textAlign = TextAlign.Center
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showSuccessDialog = null
                            selectedTab = 1 // Switch to My Orders
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = UkubiiPrimary),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Order Koo Ilaali", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    }
}

/* ========================================================================= */
/*                          CUSTOMER SHOP SCREEN                             */
/* ========================================================================= */

@Composable
fun CustomerShopScreen(
    settings: UkubiiSettings,
    orders: List<UkubiiOrder>,
    activeReservation: UkubiiOrder?,
    countdownSeconds: Int,
    onReserve: (Int) -> Unit,
    onSubmitOrder: (String, String) -> Unit,
    onCancelReservation: () -> Unit
) {
    var searchNumber by remember { mutableStateOf("") }
    var selectedRangeStart by remember { mutableIntStateOf(1) }
    var fullNameInput by remember { mutableStateOf("Gammadaa Daadhii") }
    var phoneInput by remember { mutableStateOf("0912345678") }
    var hasReceiptUploaded by remember { mutableStateOf(false) }

    val activeNumbersMap = remember(orders) {
        orders.filter { it.status in listOf("reserved", "pending", "confirmed") }
            .associateBy({ it.orderNumber }, { it.status })
    }

    val availableCount = remember(settings.totalOrders, activeNumbersMap) {
        maxOf(0, settings.totalOrders - activeNumbersMap.size)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Product Card
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = UkubiiCardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, UkubiiCardBorder)
            ) {
                Column {
                    Box(modifier = Modifier.height(180.dp).fillMaxWidth()) {
                        Image(
                            painter = painterResource(id = R.drawable.gemeda_ev_banner),
                            contentDescription = "Gemeda EV Car",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = UkubiiGreen.copy(alpha = 0.9f),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                        ) {
                            Text(
                                "ACTIVE EQUB",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            settings.productName,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            settings.description,
                            fontSize = 12.sp,
                            color = UkubiiTextMuted
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = UkubiiDarkBg,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("GATII", fontSize = 10.sp, color = UkubiiTextMuted, fontWeight = FontWeight.Bold)
                                    Text(
                                        "${NumberFormat.getNumberInstance().format(settings.ticketPrice)} Birr",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = UkubiiGold
                                    )
                                }
                                Column {
                                    Text("XUMURAMA", fontSize = 10.sp, color = UkubiiTextMuted, fontWeight = FontWeight.Bold)
                                    Text(settings.endDate, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Column {
                                    Text("LAKK HAFAN", fontSize = 10.sp, color = UkubiiTextMuted, fontWeight = FontWeight.Bold)
                                    Text("$availableCount / ${settings.totalOrders}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = UkubiiGreen)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Active Reservation Card
        if (activeReservation != null) {
            item {
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = UkubiiGold.copy(alpha = 0.15f)),
                    border = androidx.compose.foundation.BorderStroke(2.dp, UkubiiGold)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .background(UkubiiGold, CircleShape)
                                )
                                Text(
                                    "RESERVATION ACTIVE",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = UkubiiGold
                                )
                            }
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color.Black.copy(alpha = 0.5f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, UkubiiGold.copy(alpha = 0.5f))
                            ) {
                                val minutes = countdownSeconds / 60
                                val seconds = countdownSeconds % 60
                                Text(
                                    String.format("%02d:%02d", minutes, seconds),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    "Order #${activeReservation.orderNumber}",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = Color.White
                                )
                                Text(
                                    "Gatii: ${NumberFormat.getNumberInstance().format(settings.ticketPrice)} Birr",
                                    fontSize = 12.sp,
                                    color = UkubiiTextMuted
                                )
                            }
                            TextButton(onClick = onCancelReservation) {
                                Text("Haqi", color = UkubiiRed, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Step 1: Order Number Selection Grid
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = UkubiiCardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, UkubiiCardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(shape = CircleShape, color = UkubiiPrimary, modifier = Modifier.size(26.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("1", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                        Column {
                            Text("Order Number Filadhu", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Lakkoofsa banaa ta'e filadhu (1 - ${settings.totalOrders})", fontSize = 11.sp, color = UkubiiTextMuted)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Search input
                    OutlinedTextField(
                        value = searchNumber,
                        onValueChange = { searchNumber = it },
                        placeholder = { Text("Lakkoofsa barbaadi (fkn 123)...", fontSize = 13.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        trailingIcon = {
                            if (searchNumber.isNotEmpty()) {
                                IconButton(onClick = {
                                    val num = searchNumber.toIntOrNull()
                                    if (num != null && num in 1..settings.totalOrders) {
                                        selectedRangeStart = ((num - 1) / 50) * 50 + 1
                                    }
                                }) {
                                    Icon(Icons.Default.Search, contentDescription = "Search", tint = UkubiiPrimary)
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = UkubiiPrimary,
                            unfocusedBorderColor = UkubiiCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Range selection pills
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        val ranges = (1..minOf(1000, settings.totalOrders) step 50).toList()
                        items(ranges) { start ->
                            val end = minOf(start + 49, settings.totalOrders)
                            val isSelected = selectedRangeStart == start
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedRangeStart = start },
                                label = { Text("$start - $end", fontSize = 12.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = UkubiiPrimary,
                                    selectedLabelColor = Color.White,
                                    containerColor = UkubiiDarkBg,
                                    labelColor = UkubiiTextMuted
                                )
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Status Legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LegendItem(color = UkubiiCardBorder, label = "Banaa")
                        LegendItem(color = UkubiiGold, label = "Reserved")
                        LegendItem(color = Color(0xFF6366F1), label = "Pending")
                        LegendItem(color = UkubiiGreen, label = "Confirmed")
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Number Grid (Current 50 numbers slice)
                    val rangeEnd = minOf(selectedRangeStart + 49, settings.totalOrders)
                    val numbersList = (selectedRangeStart..rangeEnd).toList()

                    Box(modifier = Modifier.height(280.dp)) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(5),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(numbersList) { num ->
                                val status = activeNumbersMap[num]
                                val isSelected = activeReservation?.orderNumber == num

                                NumberCell(
                                    number = num,
                                    status = status,
                                    isSelected = isSelected,
                                    onClick = {
                                        if (status == null) {
                                            onReserve(num)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Step 2: Payment & Submit Order Form
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = UkubiiCardBg),
                border = androidx.compose.foundation.BorderStroke(1.dp, UkubiiCardBorder)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(shape = CircleShape, color = UkubiiPrimary, modifier = Modifier.size(26.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("2", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                        Column {
                            Text("Kaffaltii & Nagahee", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            Text("Odeeffannoo kee guuti nagahee upload godhi", fontSize = 11.sp, color = UkubiiTextMuted)
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Payment details banner
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = UkubiiDarkBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, UkubiiGold.copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("🏦 Bank & Telebirr Kaffaltii:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = UkubiiGold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(settings.adminAccount, fontSize = 12.sp, color = Color.White, lineHeight = 18.sp)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Gatii: ${NumberFormat.getNumberInstance().format(settings.ticketPrice)} Birr",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = UkubiiGold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    // Form Inputs
                    OutlinedTextField(
                        value = fullNameInput,
                        onValueChange = { fullNameInput = it },
                        label = { Text("Maqaa Guutuu *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = UkubiiPrimary,
                            unfocusedBorderColor = UkubiiCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedTextField(
                        value = phoneInput,
                        onValueChange = { phoneInput = it },
                        label = { Text("Lakkoofsa Bilbilaa *") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = UkubiiPrimary,
                            unfocusedBorderColor = UkubiiCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Receipt Upload Simulator
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = if (hasReceiptUploaded) UkubiiGreen.copy(alpha = 0.15f) else UkubiiDarkBg,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (hasReceiptUploaded) UkubiiGreen else UkubiiCardBorder
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { hasReceiptUploaded = !hasReceiptUploaded }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                if (hasReceiptUploaded) Icons.Default.CheckCircle else Icons.Default.UploadFile,
                                contentDescription = "Receipt",
                                tint = if (hasReceiptUploaded) UkubiiGreen else UkubiiGold
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    if (hasReceiptUploaded) "Nagaheen Upload Ta'eera" else "Nagahee Kaffaltii Upload Godhi *",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    if (hasReceiptUploaded) "receipt_cbe_payment.jpg (1.4 MB)" else "JPG, PNG, WEBP, PDF (Max 5MB)",
                                    fontSize = 11.sp,
                                    color = UkubiiTextMuted
                                )
                            }
                            if (hasReceiptUploaded) {
                                Text("Jijjiiri", fontSize = 11.sp, color = UkubiiGreen, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    val canSubmit = activeReservation != null &&
                            fullNameInput.isNotBlank() &&
                            phoneInput.isNotBlank() &&
                            hasReceiptUploaded

                    Button(
                        onClick = { onSubmitOrder(fullNameInput, phoneInput) },
                        enabled = canSubmit,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("submit_order_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = UkubiiPrimary,
                            disabledContainerColor = UkubiiPrimary.copy(alpha = 0.3f)
                        )
                    ) {
                        Text(
                            if (activeReservation == null) "DURAAN NUMBER FILADHU" else "ORDER SUBMIT GODHI",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(modifier = Modifier.size(8.dp).background(color, RoundedCornerShape(2.dp)))
        Text(label, fontSize = 10.sp, color = UkubiiTextMuted)
    }
}

@Composable
fun NumberCell(
    number: Int,
    status: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgColor = when {
        isSelected -> UkubiiPrimary
        status == "reserved" -> UkubiiGold.copy(alpha = 0.2f)
        status == "pending" -> Color(0xFF6366F1).copy(alpha = 0.25f)
        status == "confirmed" -> UkubiiGreen.copy(alpha = 0.25f)
        else -> UkubiiDarkBg
    }

    val borderColor = when {
        isSelected -> Color.White
        status == "reserved" -> UkubiiGold
        status == "pending" -> Color(0xFF818CF8)
        status == "confirmed" -> UkubiiGreen
        else -> UkubiiCardBorder
    }

    val textColor = when {
        isSelected -> Color.White
        status == "reserved" -> UkubiiGold
        status == "pending" -> Color(0xFFA5B4FC)
        status == "confirmed" -> Color(0xFF6EE7B7)
        else -> Color.White
    }

    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bgColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(enabled = status == null, onClick = onClick)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                "$number",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            if (status != null) {
                Text(
                    status.take(4),
                    fontSize = 8.sp,
                    color = textColor.copy(alpha = 0.8f),
                    maxLines = 1
                )
            }
        }
    }
}

/* ========================================================================= */
/*                          CUSTOMER ORDERS SCREEN                           */
/* ========================================================================= */

@Composable
fun CustomerOrdersScreen(orders: List<UkubiiOrder>) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Text(
            "Orderwwan Koo",
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold,
            color = Color.White
        )
        Text(
            "Seenaa order fi status isaanii asitti hordofi",
            fontSize = 12.sp,
            color = UkubiiTextMuted
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (orders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("📭", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Hamma ammaatti order homaa hin qabdu.", color = UkubiiTextMuted, fontSize = 14.sp)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(orders) { order ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = UkubiiCardBg),
                        border = androidx.compose.foundation.BorderStroke(1.dp, UkubiiCardBorder)
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Order #${order.orderNumber}",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                StatusBadge(status = order.status)
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Maqaa: ${order.fullName.ifEmpty { "N/A" }}", fontSize = 12.sp, color = UkubiiTextMuted)
                                Text("Bilbila: ${order.phone.ifEmpty { "N/A" }}", fontSize = 12.sp, color = UkubiiTextMuted)
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    "Gatii: ${NumberFormat.getNumberInstance().format(order.amount)} Birr",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = UkubiiGold
                                )
                                Text(order.createdAt, fontSize = 11.sp, color = UkubiiTextMuted)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val (bgColor, textColor) = when (status.lowercase()) {
        "pending" -> Color(0xFF6366F1).copy(alpha = 0.25f) to Color(0xFF818CF8)
        "confirmed" -> UkubiiGreen.copy(alpha = 0.25f) to Color(0xFF34D399)
        "rejected" -> UkubiiRed.copy(alpha = 0.25f) to Color(0xFFF87171)
        "reserved" -> UkubiiGold.copy(alpha = 0.25f) to UkubiiGold
        else -> UkubiiTextMuted.copy(alpha = 0.25f) to UkubiiTextMuted
    }

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = bgColor
    ) {
        Text(
            status.uppercase(),
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = textColor,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
        )
    }
}

/* ========================================================================= */
/*                             ADMIN SCREEN                                  */
/* ========================================================================= */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    settings: UkubiiSettings,
    orders: List<UkubiiOrder>,
    onConfirmOrder: (Int) -> Unit,
    onRejectOrder: (Int) -> Unit,
    onUpdateSettings: (UkubiiSettings) -> Unit
) {
    var adminTab by remember { mutableIntStateOf(0) } // 0: Orders & Stats, 1: Settings
    var filterStatus by remember { mutableStateOf("all") }
    var searchQuery by remember { mutableStateOf("") }
    var showReceiptDialog by remember { mutableStateOf<UkubiiOrder?>(null) }

    val filteredOrders = remember(orders, filterStatus, searchQuery) {
        orders.filter { order ->
            val matchStatus = filterStatus == "all" || order.status.equals(filterStatus, ignoreCase = true)
            val matchSearch = searchQuery.isBlank() ||
                    order.orderNumber.toString().contains(searchQuery) ||
                    order.fullName.contains(searchQuery, ignoreCase = true) ||
                    order.phone.contains(searchQuery) ||
                    order.telegramUsername.contains(searchQuery, ignoreCase = true)
            matchStatus && matchSearch
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Admin Header with Key status
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("UKUBII ADMIN", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text("Bulchiinsa Order & Kaffaltii", fontSize = 12.sp, color = UkubiiTextMuted)
                }
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = UkubiiGreen.copy(alpha = 0.2f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, UkubiiGreen)
                ) {
                    Text(
                        "🔑 Key: Active",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = UkubiiGreen,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }

        // Sub-tabs: Orders vs Settings
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = adminTab == 0,
                    onClick = { adminTab = 0 },
                    label = { Text("📦 Orders & Stats", fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = UkubiiPrimary,
                        selectedLabelColor = Color.White,
                        containerColor = UkubiiCardBg,
                        labelColor = UkubiiTextMuted
                    )
                )
                FilterChip(
                    selected = adminTab == 1,
                    onClick = { adminTab = 1 },
                    label = { Text("⚙️ Settings", fontWeight = FontWeight.Bold) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = UkubiiPrimary,
                        selectedLabelColor = Color.White,
                        containerColor = UkubiiCardBg,
                        labelColor = UkubiiTextMuted
                    )
                )
            }
        }

        if (adminTab == 0) {
            // Stats Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AdminStatCard(modifier = Modifier.weight(1f), label = "WALIIGALA", value = "${orders.size}", color = Color.White)
                        AdminStatCard(modifier = Modifier.weight(1f), label = "PENDING", value = "${orders.count { it.status == "pending" }}", color = UkubiiGold)
                        AdminStatCard(modifier = Modifier.weight(1f), label = "CONFIRMED", value = "${orders.count { it.status == "confirmed" }}", color = UkubiiGreen)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AdminStatCard(modifier = Modifier.weight(1f), label = "REJECTED", value = "${orders.count { it.status == "rejected" }}", color = UkubiiRed)
                        AdminStatCard(modifier = Modifier.weight(1f), label = "RESERVED", value = "${orders.count { it.status == "reserved" }}", color = UkubiiGold)
                        val occupied = orders.count { it.status in listOf("reserved", "pending", "confirmed") }
                        AdminStatCard(modifier = Modifier.weight(1f), label = "BAANAA", value = "${maxOf(0, settings.totalOrders - occupied)}", color = Color(0xFF38BDF8))
                    }
                }
            }

            // Filters & Search
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Barbaadi (Order #, Maqaa, Bilbila)...", fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = UkubiiTextMuted) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = UkubiiPrimary,
                            unfocusedBorderColor = UkubiiCardBorder,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        )
                    )

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val filters = listOf("all" to "Hunda", "pending" to "Pending", "confirmed" to "Confirmed", "rejected" to "Rejected", "reserved" to "Reserved")
                        items(filters) { (key, label) ->
                            FilterChip(
                                selected = filterStatus == key,
                                onClick = { filterStatus = key },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = UkubiiPrimary,
                                    selectedLabelColor = Color.White,
                                    containerColor = UkubiiCardBg,
                                    labelColor = UkubiiTextMuted
                                )
                            )
                        }
                    }
                }
            }

            // Orders List
            items(filteredOrders) { order ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = UkubiiCardBg),
                    border = androidx.compose.foundation.BorderStroke(1.dp, UkubiiCardBorder)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Order #${order.orderNumber}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                            StatusBadge(status = order.status)
                        }

                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Maqaa: ${order.fullName.ifEmpty { "(Hin guutamne)" }}",
                            fontSize = 13.sp,
                            color = Color.White
                        )
                        Text(
                            "Bilbila: ${order.phone.ifEmpty { "(Hin guutamne)" }} | TG: @${order.telegramUsername.ifEmpty { order.telegramId }}",
                            fontSize = 12.sp,
                            color = UkubiiTextMuted
                        )
                        Text(
                            "Gatii: ${NumberFormat.getNumberInstance().format(order.amount)} Birr",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = UkubiiGold
                        )

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { showReceiptDialog = order },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                            ) {
                                Text("🧾 Nagahee", fontSize = 11.sp)
                            }

                            if (order.status == "pending") {
                                Button(
                                    onClick = { onConfirmOrder(order.id) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = UkubiiGreen)
                                ) {
                                    Text("Confirm", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                Button(
                                    onClick = { onRejectOrder(order.id) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(8.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = UkubiiRed)
                                ) {
                                    Text("Reject", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        } else {
            // Admin Settings Form
            item {
                AdminSettingsForm(
                    settings = settings,
                    onSave = onUpdateSettings
                )
            }
        }
    }

    // Receipt Dialog
    showReceiptDialog?.let { order ->
        AlertDialog(
            onDismissRequest = { showReceiptDialog = null },
            containerColor = UkubiiCardBg,
            title = { Text("Nagahee Order #${order.orderNumber}", color = Color.White, fontSize = 16.sp) },
            text = {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = UkubiiDarkBg,
                        border = androidx.compose.foundation.BorderStroke(1.dp, UkubiiCardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("📄", fontSize = 48.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    order.receiptFilename.ifEmpty { "nagahee_kaffaltii.jpg" },
                                    fontSize = 12.sp,
                                    color = UkubiiGreen,
                                    fontWeight = FontWeight.Bold
                                )
                                Text("Mirkanaa'eera: 3,500 Birr", fontSize = 11.sp, color = UkubiiTextMuted)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReceiptDialog = null }) {
                    Text("Cufi", color = UkubiiPrimary)
                }
            }
        )
    }
}

@Composable
fun AdminStatCard(modifier: Modifier, label: String, value: String, color: Color) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = UkubiiCardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, UkubiiCardBorder)
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Text(label, fontSize = 9.sp, color = UkubiiTextMuted, fontWeight = FontWeight.Bold)
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold, color = color)
        }
    }
}

@Composable
fun AdminSettingsForm(
    settings: UkubiiSettings,
    onSave: (UkubiiSettings) -> Unit
) {
    var productName by remember { mutableStateOf(settings.productName) }
    var priceStr by remember { mutableStateOf(settings.ticketPrice.toString()) }
    var totalOrdersStr by remember { mutableStateOf(settings.totalOrders.toString()) }
    var endDate by remember { mutableStateOf(settings.endDate) }
    var adminName by remember { mutableStateOf(settings.adminName) }
    var adminAccount by remember { mutableStateOf(settings.adminAccount) }
    var desc by remember { mutableStateOf(settings.description) }

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = UkubiiCardBg),
        border = androidx.compose.foundation.BorderStroke(1.dp, UkubiiCardBorder)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Qindaa'ina Shop (Settings)", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color.White)

            OutlinedTextField(
                value = productName,
                onValueChange = { productName = it },
                label = { Text("Maqaa Product") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = UkubiiPrimary,
                    unfocusedBorderColor = UkubiiCardBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = priceStr,
                    onValueChange = { priceStr = it },
                    label = { Text("Gatii (Birr)") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = UkubiiPrimary,
                        unfocusedBorderColor = UkubiiCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )

                OutlinedTextField(
                    value = totalOrdersStr,
                    onValueChange = { totalOrdersStr = it },
                    label = { Text("Waliigala Orders") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = UkubiiPrimary,
                        unfocusedBorderColor = UkubiiCardBorder,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            }

            OutlinedTextField(
                value = endDate,
                onValueChange = { endDate = it },
                label = { Text("Guyyaa Xumuramaa (YYYY-MM-DD)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = UkubiiPrimary,
                    unfocusedBorderColor = UkubiiCardBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            OutlinedTextField(
                value = adminAccount,
                onValueChange = { adminAccount = it },
                label = { Text("Odeeffannoo Bank / Kaffaltii") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = UkubiiPrimary,
                    unfocusedBorderColor = UkubiiCardBorder,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                )
            )

            Button(
                onClick = {
                    onSave(
                        settings.copy(
                            productName = productName,
                            ticketPrice = priceStr.toIntOrNull() ?: 3500,
                            totalOrders = totalOrdersStr.toIntOrNull() ?: 5000,
                            endDate = endDate,
                            adminAccount = adminAccount,
                            description = desc,
                            adminName = adminName
                        )
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = UkubiiPrimary)
            ) {
                Text("💾 Save Godhi", fontWeight = FontWeight.Bold)
            }
        }
    }
}
