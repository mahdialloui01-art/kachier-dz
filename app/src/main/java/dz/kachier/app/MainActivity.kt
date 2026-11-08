package dz.kachier.app

import android.content.Context
import android.os.Bundle
import android.util.Base64
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.room.withTransaction
import dz.kachier.app.data.*
import kotlinx.coroutines.launch
import java.security.SecureRandom
import java.util.Locale
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

private enum class Profile { SELLER, STOCK }

class MainActivity : ComponentActivity() {
 private val db by lazy { ShopDatabase.get(this) }
 private val dao by lazy { db.dao() }
 private val prefs by lazy { getSharedPreferences("profile_security", Context.MODE_PRIVATE) }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  setContent {
   MaterialTheme(colorScheme = lightColorScheme(primary = androidx.compose.ui.graphics.Color(0xFF176B55))) {
    var profile by remember { mutableStateOf<Profile?>(null) }
    var configured by remember { mutableStateOf(prefs.contains("seller_hash") && prefs.contains("stock_hash")) }
    if (!configured) PasswordSetup { seller, stock ->
     prefs.edit().putString("seller_hash", hashPassword(seller)).putString("stock_hash", hashPassword(stock)).apply()
     configured = true
    } else if (profile == null) ProfileLogin(
     verify = { role, pass ->
      val key = if (role == Profile.SELLER) "seller_hash" else "stock_hash"
      prefs.getString(key, null)?.let { verifyPassword(pass, it) } == true
     }, onLogin = { profile = it }
    ) else Cashier(
     profile = profile!!,
     load = { dao.getProducts() },
     add = { dao.addProduct(it) },
     sale = { cart -> db.withTransaction {
      val current = dao.getProducts().associateBy { it.id }
      require(cart.all { (p,n) -> n > 0 && (current[p.id]?.stock ?: -1) >= n }) { "المخزون غير كافٍ، حدّث القائمة وحاول مجدداً" }
      val total = cart.entries.sumOf { (p,n) -> p.sellPrice*n }
      val id = dao.addSale(Sale(total=total))
      dao.addItems(cart.map { (p,n) -> SaleItem(saleId=id,productId=p.id,productName=p.name,quantity=n,unitPrice=p.sellPrice,unitBuyPrice=p.buyPrice) })
      cart.forEach { (p,n) -> val item=current.getValue(p.id); dao.updateProduct(item.copy(stock=item.stock-n)) }
     } },
     daily = { dao.totalSince(startOfDay()) }, profit = { dao.profitSince(startOfDay()) },
     logout = { profile = null }
    )
   }
  }
 }
}

private fun startOfDay() = java.util.Calendar.getInstance().apply {
 set(java.util.Calendar.HOUR_OF_DAY,0); set(java.util.Calendar.MINUTE,0); set(java.util.Calendar.SECOND,0); set(java.util.Calendar.MILLISECOND,0)
}.timeInMillis

private fun hashPassword(password: String): String {
 val salt=ByteArray(16).also { SecureRandom().nextBytes(it) }
 val spec=PBEKeySpec(password.toCharArray(),salt,120000,256)
 val hash=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
 spec.clearPassword()
 return Base64.encodeToString(salt,Base64.NO_WRAP)+":"+Base64.encodeToString(hash,Base64.NO_WRAP)
}
private fun verifyPassword(password:String,stored:String):Boolean = try {
 val parts=stored.split(":"); val salt=Base64.decode(parts[0],Base64.NO_WRAP); val expected=Base64.decode(parts[1],Base64.NO_WRAP)
 val spec=PBEKeySpec(password.toCharArray(),salt,120000,256)
 val actual=SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
 spec.clearPassword(); java.security.MessageDigest.isEqual(expected,actual)
} catch (_:Exception) { false }

@Composable
private fun PasswordSetup(save:(String,String)->Unit) {
 var seller by remember { mutableStateOf("") }; var seller2 by remember { mutableStateOf("") }
 var stock by remember { mutableStateOf("") }; var stock2 by remember { mutableStateOf("") }
 var error by remember { mutableStateOf<String?>(null) }
 Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center) {
  Text("إعداد كلمات المرور",style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)
  Text("أنشئ كلمة مرور مستقلة لكل بروفايل.")
  OutlinedTextField(seller,{seller=it},label={Text("كلمة مرور البائع")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
  OutlinedTextField(seller2,{seller2=it},label={Text("تأكيد كلمة مرور البائع")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
  Spacer(Modifier.height(8.dp))
  OutlinedTextField(stock,{stock=it},label={Text("كلمة مرور مسؤول الستوك")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
  OutlinedTextField(stock2,{stock2=it},label={Text("تأكيد كلمة مرور الستوك")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
  error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
  Button(onClick={
   error=when { seller.length<4 || stock.length<4 -> "كلمة المرور لازم تكون 4 أحرف أو أرقام على الأقل"
    seller!=seller2 || stock!=stock2 -> "تأكيد كلمة المرور غير مطابق"
    seller==stock -> "اختار كلمتي مرور مختلفتين"
    else -> null }
   if(error==null) save(seller,stock)
  },modifier=Modifier.fillMaxWidth().padding(top=12.dp)) { Text("حفظ كلمات المرور") }
 }
}

@Composable
private fun ProfileLogin(verify:(Profile,String)->Boolean,onLogin:(Profile)->Unit) {
 var selected by remember { mutableStateOf(Profile.SELLER) }; var password by remember { mutableStateOf("") }; var error by remember { mutableStateOf<String?>(null) }
 Column(Modifier.fillMaxSize().padding(24.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally) {
  Text("Kachier DZ",style=MaterialTheme.typography.headlineLarge,fontWeight=FontWeight.Bold)
  Text("اختار البروفايل وسجّل الدخول")
  Spacer(Modifier.height(20.dp))
  Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
   FilterChip(selected==Profile.SELLER,{selected=Profile.SELLER;password="";error=null},label={Text("البائع")})
   FilterChip(selected==Profile.STOCK,{selected=Profile.STOCK;password="";error=null},label={Text("مسؤول الستوك")})
  }
  OutlinedTextField(password,{password=it;error=null},label={Text("كلمة المرور")},visualTransformation=PasswordVisualTransformation(),modifier=Modifier.fillMaxWidth())
  error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
  Button(onClick={if(verify(selected,password)) onLogin(selected) else error="كلمة المرور غير صحيحة"},modifier=Modifier.fillMaxWidth().padding(top=12.dp)) { Text("دخول") }
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Cashier(profile:Profile,load:suspend()->List<Product>,add:suspend(Product)->Long,sale:suspend(Map<Product,Int>)->Unit,daily:suspend()->Double,profit:suspend()->Double,logout:()->Unit) {
 val scope= rememberCoroutineScope()
 var products by remember { mutableStateOf<List<Product>>(emptyList()) }; var cart by remember { mutableStateOf<Map<Product,Int>>(emptyMap()) }
 var today by remember { mutableDoubleStateOf(0.0) }; var todayProfit by remember { mutableDoubleStateOf(0.0) }
 var addOpen by remember { mutableStateOf(false) }; var name by remember { mutableStateOf("") }; var buy by remember { mutableStateOf("") }; var price by remember { mutableStateOf("") }; var qty by remember { mutableStateOf("0") }
 var alert by remember { mutableStateOf<String?>(null) }
 val isStock=profile==Profile.STOCK
 fun refresh(){scope.launch{products=load();today=daily();todayProfit=profit()}}
 LaunchedEffect(Unit){refresh()}
 val total=cart.entries.sumOf{(p,n)->p.sellPrice*n}
 fun da(v:Double)=String.format(Locale.US,"%.2f دج",v)
 Scaffold(topBar={TopAppBar(title={Text(if(isStock)"إدارة الستوك" else "Kachier DZ – البائع",fontWeight=FontWeight.Bold)},actions={
  if(isStock) TextButton(onClick={addOpen=true}){Text("+ سلعة")}
  TextButton(onClick=logout){Text("خروج")}
 })},bottomBar={if(!isStock) Surface(shadowElevation=6.dp){Column(Modifier.fillMaxWidth().padding(16.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("المجموع",fontWeight=FontWeight.Bold);Text(da(total),fontWeight=FontWeight.Bold)}
  Spacer(Modifier.height(8.dp))
  Button(onClick={scope.launch{try{sale(cart);cart=emptyMap();alert="تم تسجيل البيع بنجاح";refresh()}catch(e:Exception){alert=e.message?:"تعذر تسجيل البيع"}}},enabled=cart.isNotEmpty(),modifier=Modifier.fillMaxWidth()){Text("إتمام البيع")}
 }}}){pad->
  Column(Modifier.fillMaxSize().padding(pad).padding(horizontal=16.dp)){
   if(isStock) Card(Modifier.fillMaxWidth().padding(top=10.dp)){Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(6.dp)){
    Text("ملخص الحسابات",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
    Text("مبيعات اليوم: "+da(today));Text("الربح الإجمالي المحقق اليوم: "+da(todayProfit))
    Text("قيمة المخزون بسعر الشراء: "+da(products.sumOf{it.buyPrice*it.stock}))
    Text("القيمة المتوقعة عند بيع المخزون: "+da(products.sumOf{it.sellPrice*it.stock}))
    Text("الربح المتوقع من المخزون: "+da(products.sumOf{(it.sellPrice-it.buyPrice)*it.stock}))
    Text("عدد الوحدات: "+products.sumOf{it.stock})
   }} else Card(Modifier.fillMaxWidth().padding(top=10.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){
    Column(Modifier.padding(16.dp)){Text("مبيعات اليوم");Text(da(today),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)}
   }
   Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(if(isStock)"المنتجات والمخزون" else "السلع للبيع",style=MaterialTheme.typography.titleLarge);Text(products.size.toString()+" منتج")}
   if(products.isEmpty()) Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){Text(if(isStock)"أضف أول سلعة من الأعلى" else "لا توجد سلع بعد. اطلب من مسؤول الستوك إضافتها.")}
   else LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){items(products,key={it.id}){p->
    Card(Modifier.fillMaxWidth().then(if(!isStock)Modifier.clickable{val n=cart[p]?:0;if(p.stock>n)cart=cart+(p to n+1)else alert="المخزون غير كافٍ"}else Modifier)){
     Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){
      Column(Modifier.weight(1f)){Text(p.name,fontWeight=FontWeight.SemiBold);Text("المخزون: "+p.stock);if(isStock)Text("شراء: "+da(p.buyPrice)+" | بيع: "+da(p.sellPrice)+" | ربح الوحدة: "+da(p.sellPrice-p.buyPrice))}
      if(!isStock){Text(da(p.sellPrice),fontWeight=FontWeight.Bold);TextButton(onClick={val n=cart[p]?:0;if(p.stock>n)cart=cart+(p to n+1)else alert="المخزون غير كافٍ"}){Text("＋")}}
     }
    }
   }}
   if(!isStock&&cart.isNotEmpty())Text("السلة: "+cart.entries.joinToString("، "){"\${it.key.name} × \${it.value}"})
  }
 }
 if(addOpen) AlertDialog(onDismissRequest={addOpen=false},title={Text("إضافة سلعة")},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
  OutlinedTextField(name,{name=it},label={Text("اسم السلعة")},singleLine=true)
  OutlinedTextField(buy,{buy=it},label={Text("سعر الشراء")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
  OutlinedTextField(price,{price=it},label={Text("سعر البيع")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
  OutlinedTextField(qty,{qty=it},label={Text("الكمية")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
 }},confirmButton={TextButton(onClick={val b=buy.toDoubleOrNull();val p=price.toDoubleOrNull();val n=qty.toIntOrNull()
  if(name.isNotBlank()&&b!=null&&b>=0&&p!=null&&p>=0&&n!=null&&n>=0)scope.launch{add(Product(name=name.trim(),buyPrice=b,sellPrice=p,stock=n));name="";buy="";price="";qty="0";addOpen=false;refresh()}else alert="تأكد من الاسم وسعر الشراء والبيع والكمية"
 }){Text("حفظ")}},dismissButton={TextButton(onClick={addOpen=false}){Text("إلغاء")}})
 alert?.let{msg->AlertDialog(onDismissRequest={alert=null},confirmButton={TextButton(onClick={alert=null}){Text("حسناً")}},text={Text(msg)})}
}
