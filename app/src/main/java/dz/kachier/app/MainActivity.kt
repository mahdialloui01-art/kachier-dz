package dz.kachier.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dz.kachier.app.data.*
import kotlinx.coroutines.launch
import java.util.Locale

class MainActivity : ComponentActivity() {
 private val dao by lazy { ShopDatabase.get(this).dao() }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  setContent {
   MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF176B55))) {
    Cashier(
     load = { dao.getProducts() }, add = { dao.addProduct(it) },
     sale = { cart ->
      val sum = cart.entries.sumOf { (p,n) -> p.sellPrice*n }
      val id = dao.addSale(Sale(total=sum))
      dao.addItems(cart.map { (p,n) -> SaleItem(saleId=id, productId=p.id, productName=p.name, quantity=n, unitPrice=p.sellPrice) })
      cart.forEach { (p,n) -> dao.updateProduct(p.copy(stock=(p.stock-n).coerceAtLeast(0))) }
     },
     daily = {
      val c = java.util.Calendar.getInstance().apply {
       set(java.util.Calendar.HOUR_OF_DAY,0); set(java.util.Calendar.MINUTE,0); set(java.util.Calendar.SECOND,0); set(java.util.Calendar.MILLISECOND,0)
      }
      dao.totalSince(c.timeInMillis)
     }
    )
   }
  }
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Cashier(load: suspend () -> List<Product>, add: suspend (Product) -> Long, sale: suspend (Map<Product,Int>) -> Unit, daily: suspend () -> Double) {
 val scope = rememberCoroutineScope()
 var products by remember { mutableStateOf<List<Product>>(emptyList()) }
 var cart by remember { mutableStateOf<Map<Product,Int>>(emptyMap()) }
 var today by remember { mutableDoubleStateOf(0.0) }
 var addOpen by remember { mutableStateOf(false) }
 var name by remember { mutableStateOf("") }
 var buy by remember { mutableStateOf("") }
 var price by remember { mutableStateOf("") }
 var qty by remember { mutableStateOf("0") }
 var alert by remember { mutableStateOf<String?>(null) }
 fun refresh() { scope.launch { products=load(); today=daily() } }
 LaunchedEffect(Unit) { refresh() }
 val total = cart.entries.sumOf { (p,n) -> p.sellPrice*n }
 fun da(v:Double) = String.format(Locale.US,"%.2f DA",v)
 Scaffold(
  topBar={ TopAppBar(title={ Text("Kachier DZ",fontWeight=FontWeight.Bold) },actions={ TextButton(onClick={addOpen=true}) { Text("+ سلعة") } }) },
  bottomBar={
   Surface(shadowElevation=6.dp) {
    Column(Modifier.fillMaxWidth().padding(16.dp)) {
     Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) { Text("المجموع",fontWeight=FontWeight.Bold); Text(da(total),fontWeight=FontWeight.Bold) }
     Spacer(Modifier.height(8.dp))
     Button(onClick={ scope.launch { sale(cart); cart=emptyMap(); alert="تم تسجيل البيع"; refresh() } },enabled=cart.isNotEmpty(),modifier=Modifier.fillMaxWidth()) { Text("إتمام البيع") }
    }
   }
  }
 ) { pad ->
  Column(Modifier.fillMaxSize().padding(pad).padding(horizontal=16.dp)) {
   Card(Modifier.fillMaxWidth().padding(top=10.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)) {
    Column(Modifier.padding(16.dp)) { Text("مبيعات اليوم"); Text(da(today),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold) }
   }
   Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.SpaceBetween) { Text("السلع",style=MaterialTheme.typography.titleLarge); Text("${products.size} منتج") }
   if(products.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) { Text("أضف أول سلعة من الزر في الأعلى") }
   else LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)) {
    items(products,key={it.id}) { p ->
     Card(Modifier.fillMaxWidth().clickable {
      val n=cart[p]?:0
      if(p.stock>n) cart=cart+(p to n+1) else alert="المخزون غير كافٍ"
     }) {
      Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically) {
       Column(Modifier.weight(1f)) { Text(p.name,fontWeight=FontWeight.SemiBold); Text("المخزون: ${p.stock}") }
       Text(da(p.sellPrice),fontWeight=FontWeight.Bold)
       TextButton(onClick={ val n=cart[p]?:0; if(p.stock>n) cart=cart+(p to n+1) }) { Text("＋") }
      }
     }
    }
   }
   if(cart.isNotEmpty()) Text("السلة: "+cart.entries.joinToString("، ") { "${it.key.name} × ${it.value}" })
  }
 }
 if(addOpen) AlertDialog(
  onDismissRequest={addOpen=false},title={Text("إضافة سلعة")},
  text={ Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
   OutlinedTextField(name,{name=it},label={Text("اسم السلعة")},singleLine=true)
   OutlinedTextField(buy,{buy=it},label={Text("سعر الشراء")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
   OutlinedTextField(price,{price=it},label={Text("سعر البيع")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
   OutlinedTextField(qty,{qty=it},label={Text("الكمية")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
  } },
  confirmButton={ TextButton(onClick={
   val p=price.toDoubleOrNull(); val n=qty.toIntOrNull()
   if(name.isNotBlank() && p!=null && p>=0 && n!=null && n>=0) scope.launch {
    add(Product(name=name.trim(),buyPrice=buy.toDoubleOrNull()?:0.0,sellPrice=p,stock=n))
    name="";buy="";price="";qty="0";addOpen=false;refresh()
   } else alert="تأكد من الاسم والسعر والكمية"
  }) { Text("حفظ") } },
  dismissButton={ TextButton(onClick={addOpen=false}) { Text("إلغاء") } }
 )
 alert?.let { msg -> AlertDialog(onDismissRequest={alert=null},confirmButton={TextButton(onClick={alert=null}) { Text("حسناً") }},text={Text(msg)}) }
}