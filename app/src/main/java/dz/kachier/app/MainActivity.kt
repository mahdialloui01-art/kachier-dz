package dz.kachier.app

import android.content.Context
import android.os.Bundle
import android.util.Base64
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Color
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

private fun categoryColor(category: String): Color = when (category) {
 "سجائر وتبغ" -> Color(0xFFE9E0D5)
 "شوكولا وحلويات" -> Color(0xFFF2E1D7)
 "علكة وبونبون" -> Color(0xFFF5E4ED)
 "مشروبات ومياه" -> Color(0xFFDCECF3)
 "شيبس ومقرمشات" -> Color(0xFFF4E9CF)
 "بسكويت وكيك" -> Color(0xFFF0E5D1)
 "قهوة وشاي" -> Color(0xFFE7DED0)
 "مواد غذائية" -> Color(0xFFE3EBD8)
 "مواد تنظيف" -> Color(0xFFDCEBE9)
 "نظافة شخصية" -> Color(0xFFE5E3F0)
 "لوازم الهاتف" -> Color(0xFFDDE5F2)
 "بطاريات وولاعات" -> Color(0xFFF3E2CF)
 "مثلجات" -> Color(0xFFDCEEF0)
 else -> Color(0xFFE9E9E3)
}

class MainActivity : ComponentActivity() {
 private val db by lazy { ShopDatabase.get(this) }
 private val dao by lazy { db.dao() }
 private val prefs by lazy { getSharedPreferences("profile_security", Context.MODE_PRIVATE) }
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  setContent {
   MaterialTheme(colorScheme = lightColorScheme(
     primary = Color(0xFF174B3A), onPrimary = Color.White,
     primaryContainer = Color(0xFFE0EEE6), onPrimaryContainer = Color(0xFF123629),
     secondary = Color(0xFFB58A3A), onSecondary = Color.White,
     secondaryContainer = Color(0xFFF4EBD8), onSecondaryContainer = Color(0xFF59431B),
     tertiary = Color(0xFF52647A), onTertiary = Color.White,
     tertiaryContainer = Color(0xFFE6EBF1), onTertiaryContainer = Color(0xFF26384D),
     background = Color(0xFFF0F5F1), onBackground = Color(0xFF202B25),
     surface = Color(0xFFFFFFFF), onSurface = Color(0xFF202B25),
     surfaceVariant = Color(0xFFE4EDE7), onSurfaceVariant = Color(0xFF526158),
     error = Color(0xFFB64040), errorContainer = Color(0xFFF8E4E1)
    )) {
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
     update = { dao.updateProduct(it) }, remove = { dao.deleteProduct(it) },
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
private fun Cashier(profile:Profile,load:suspend()->List<Product>,add:suspend(Product)->Long,update:suspend(Product)->Unit,remove:suspend(Product)->Unit,sale:suspend(Map<Product,Int>)->Unit,daily:suspend()->Double,profit:suspend()->Double,logout:()->Unit) {
 val scope= rememberCoroutineScope()
 var products by remember { mutableStateOf<List<Product>>(emptyList()) }; var cart by remember { mutableStateOf<Map<Product,Int>>(emptyMap()) }
 var today by remember { mutableDoubleStateOf(0.0) }; var todayProfit by remember { mutableDoubleStateOf(0.0) }
 var addOpen by remember { mutableStateOf(false) }; var editing by remember { mutableStateOf<Product?>(null) }; var deleting by remember { mutableStateOf<Product?>(null) }; var name by remember { mutableStateOf("") }; var buy by remember { mutableStateOf("") }; var price by remember { mutableStateOf("") }; var qty by remember { mutableStateOf("0") }; var category by remember { mutableStateOf("متنوعة") }; var barcode by remember { mutableStateOf("") }; var minStock by remember { mutableStateOf("5") }
 var alert by remember { mutableStateOf<String?>(null) }
 val isStock=profile==Profile.STOCK
 var stockSection by remember { mutableStateOf("الرئيسية") }
 var searchText by remember { mutableStateOf("") }
 val productCategories = listOf("سجائر وتبغ","شوكولا وحلويات","علكة وبونبون","مشروبات ومياه","شيبس ومقرمشات","بسكويت وكيك","قهوة وشاي","مواد غذائية","مواد تنظيف","نظافة شخصية","لوازم الهاتف","بطاريات وولاعات","مثلجات","متنوعة")
 var selectedCategory by remember { mutableStateOf("الكل") }
 fun refresh(){scope.launch{products=load();today=daily();todayProfit=profit()}}
 LaunchedEffect(Unit){refresh()}
 val total=cart.entries.sumOf{(p,n)->p.sellPrice*n}
 fun da(v:Double)=String.format(Locale.US,"%.2f دج",v)
 Scaffold(containerColor=Color(0xFFF0F5F1),topBar={TopAppBar(colors=TopAppBarDefaults.topAppBarColors(containerColor=Color(0xFF174B3A),titleContentColor=Color.White,actionIconContentColor=Color.White),title={Text(if(isStock)"إدارة الستوك" else "Kachier DZ – البائع",fontWeight=FontWeight.Bold)},actions={
  if(isStock) TextButton(onClick={editing=null;name="";buy="";price="";qty="0";category="متنوعة";barcode="";minStock="5";addOpen=true}){Text("+ سلعة")}
  TextButton(onClick=logout){Text("خروج")}
 })},bottomBar={if(!isStock) Surface(shadowElevation=6.dp){Column(Modifier.fillMaxWidth().padding(16.dp)){
  Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text("المجموع",fontWeight=FontWeight.Bold);Text(da(total),fontWeight=FontWeight.Bold)}
  Spacer(Modifier.height(8.dp))
  Button(onClick={scope.launch{try{sale(cart);cart=emptyMap();alert="تم تسجيل البيع بنجاح";refresh()}catch(e:Exception){alert=e.message?:"تعذر تسجيل البيع"}}},enabled=cart.isNotEmpty(),modifier=Modifier.fillMaxWidth()){Text("إتمام البيع")}
 }}}){pad->
  Column(Modifier.fillMaxSize().padding(pad).padding(horizontal=16.dp)){
   if(isStock) {
    Row(Modifier.fillMaxWidth().padding(top=10.dp,bottom=8.dp),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
     listOf("الرئيسية","المنتجات","التنبيهات","التقارير").forEach { section ->
      FilterChip(selected=stockSection==section,onClick={stockSection=section},label={Text(section)})
     }
    }
    when(stockSection) {
     "الرئيسية" -> Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
      Text("لوحة قيادة الستوك",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
      Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
       Card(Modifier.weight(1f),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){Column(Modifier.padding(12.dp)){Text("قيمة الشراء",style=MaterialTheme.typography.labelMedium);Text(da(products.sumOf{it.buyPrice*it.stock}),fontWeight=FontWeight.Bold);Text("المخزون الحالي",style=MaterialTheme.typography.labelSmall)}}
       Card(Modifier.weight(1f),colors=CardDefaults.cardColors(containerColor=Color(0xFFF4EBD8))){Column(Modifier.padding(12.dp)){Text("قيمة البيع المتوقعة",style=MaterialTheme.typography.labelMedium);Text(da(products.sumOf{it.sellPrice*it.stock}),fontWeight=FontWeight.Bold);Text("ليست ربحاً محققاً",style=MaterialTheme.typography.labelSmall)}}
      }
      Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
       Card(Modifier.weight(1f),colors=CardDefaults.cardColors(containerColor=Color(0xFFE2EEE5))){Column(Modifier.padding(12.dp)){Text("ربح اليوم",style=MaterialTheme.typography.labelMedium);Text(da(todayProfit),fontWeight=FontWeight.Bold);Text("محقق من المبيعات",style=MaterialTheme.typography.labelSmall)}}
       Card(Modifier.weight(1f),colors=CardDefaults.cardColors(containerColor=Color(0xFFE5EAF0))){Column(Modifier.padding(12.dp)){Text("مبيعات اليوم",style=MaterialTheme.typography.labelMedium);Text(da(today),fontWeight=FontWeight.Bold);Text(products.size.toString()+" صنف",style=MaterialTheme.typography.labelSmall)}}
      }
      val lowCount=products.count{it.stock<=it.minStock}
      Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=if(lowCount>0) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer)){
       Row(Modifier.fillMaxWidth().padding(12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween){
        Column(Modifier.weight(1f)){Text(if(lowCount>0)"تنبيه المخزون" else "المخزون بحالة جيدة",fontWeight=FontWeight.Bold);Text(if(lowCount>0)"$lowCount سلعة وصلت للحد الأدنى (5 وحدات أو أقل)" else "لا توجد سلع منخفضة حالياً",style=MaterialTheme.typography.bodySmall)}
        TextButton(onClick={stockSection="التنبيهات"}){Text("عرض")}
       }
      }
      Text("الربح المتوقع من المخزون: "+da(products.sumOf{(it.sellPrice-it.buyPrice)*it.stock}),style=MaterialTheme.typography.bodyMedium)
     }
     "المنتجات" -> Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
      OutlinedTextField(searchText,{searchText=it},modifier=Modifier.fillMaxWidth(),singleLine=true,label={Text("ابحث عن سلعة")})
      Text("اختر الصنف لعرض سلعه فقط",style=MaterialTheme.typography.labelMedium)
      Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
       (listOf("الكل") + productCategories).forEach { c ->
        val count=if(c=="الكل") products.size else products.count{it.category==c}
        FilterChip(selected=selectedCategory==c,onClick={selectedCategory=c},colors=FilterChipDefaults.filterChipColors(
         selectedContainerColor=if(c=="الكل") MaterialTheme.colorScheme.primary else categoryColor(c),
         selectedLabelColor=if(c=="الكل") Color.White else Color(0xFF293128),
         containerColor=MaterialTheme.colorScheme.surface),label={Text("$c ($count)")})
       }
      }
     }
     "التنبيهات" -> {
      val low=products.filter{it.stock<=it.minStock}
      Card(Modifier.fillMaxWidth(),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.errorContainer)){Column(Modifier.padding(12.dp)){Text("السلع الناقصة",fontWeight=FontWeight.Bold);Text("يُحسب التنبيه حسب الحد الأدنى المحدد لكل سلعة")}}
      low.forEach { p -> ListItem(headlineContent={Text(p.name)},supportingContent={Text("المتبقي: ${p.stock} | سعر البيع: ${da(p.sellPrice)}")},leadingContent={Text("⚠")}) }
     }
     "التقارير" -> Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
      Text("ملخص الأداء اليومي",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)
      Card { Column(Modifier.padding(14.dp)){Text("إجمالي المبيعات اليوم");Text(da(today),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)} }
      Card { Column(Modifier.padding(14.dp)){Text("الربح الإجمالي المحقق اليوم");Text(da(todayProfit),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)} }
      Card { Column(Modifier.padding(14.dp)){Text("قيمة المخزون بسعر الشراء");Text(da(products.sumOf{it.buyPrice*it.stock}),style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)} }
      Text("التقارير التاريخية والمقارنة الشهرية ستضاف بعد بناء سجل المشتريات والمصاريف.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
     }
    }
   } else Card(Modifier.fillMaxWidth().padding(top=10.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.primaryContainer)){
    Column(Modifier.padding(16.dp)){Text("مبيعات اليوم");Text(da(today),style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold)}
   }
   if(!isStock || stockSection=="الرئيسية" || stockSection=="المنتجات") Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.SpaceBetween){Text(if(isStock)"المنتجات والمخزون" else "السلع للبيع",style=MaterialTheme.typography.titleLarge);Text(products.size.toString()+" منتج")}
   val shownProducts=if(isStock && stockSection=="التنبيهات") emptyList() else if(isStock && stockSection=="التقارير") emptyList() else if(isStock && stockSection=="المنتجات") products.filter{(selectedCategory=="الكل" || it.category==selectedCategory) && (searchText.isBlank() || it.name.contains(searchText.trim(),ignoreCase=true) || it.category.contains(searchText.trim(),ignoreCase=true) || (it.barcode?.contains(searchText.trim())==true))} else products
   if(shownProducts.isEmpty() && (stockSection=="الرئيسية" || stockSection=="المنتجات" || !isStock)) Box(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center){Text(if(isStock)"لا توجد سلع مطابقة. أضف سلعة من الأعلى." else "لا توجد سلع بعد. اطلب من مسؤول الستوك إضافتها.")}
   else if(shownProducts.isNotEmpty()) LazyColumn(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){items(shownProducts,key={it.id}){p->
    Card(Modifier.fillMaxWidth().then(if(!isStock)Modifier.clickable{val n=cart[p]?:0;if(p.stock>n)cart=cart+(p to n+1)else alert="المخزون غير كافٍ"}else Modifier.clickable{editing=p;name=p.name;buy=p.buyPrice.toString();price=p.sellPrice.toString();qty=p.stock.toString();category=p.category;barcode=p.barcode.orEmpty();minStock=p.minStock.toString();addOpen=true})){
     Row(Modifier.fillMaxWidth().padding(14.dp),verticalAlignment=Alignment.CenterVertically){
      Column(Modifier.weight(1f)){Text(p.name,fontWeight=FontWeight.Bold,color=Color(0xFF26352D));Text(p.category+" • المخزون: "+p.stock,color=MaterialTheme.colorScheme.onSurfaceVariant);if(isStock)Text("شراء: "+da(p.buyPrice)+" | بيع: "+da(p.sellPrice)+" | ربح الوحدة: "+da(p.sellPrice-p.buyPrice));if(isStock && p.stock<=p.minStock)Text("⚠ وصل للحد الأدنى: "+p.minStock,color=MaterialTheme.colorScheme.error)}
      if(!isStock){Text(da(p.sellPrice),fontWeight=FontWeight.Bold);TextButton(onClick={val n=cart[p]?:0;if(p.stock>n)cart=cart+(p to n+1)else alert="المخزون غير كافٍ"}){Text("＋")}}
     }
    }
   }}
   if(!isStock&&cart.isNotEmpty())Text("السلة: "+cart.entries.joinToString("، "){"\${it.key.name} × \${it.value}"})
  }
 }
 if(addOpen) AlertDialog(onDismissRequest={addOpen=false},title={Text(if(editing==null)"إضافة سلعة" else "تعديل السلعة")},text={Column(verticalArrangement=Arrangement.spacedBy(6.dp)){
  OutlinedTextField(name,{name=it},label={Text("اسم السلعة")},singleLine=true)
  var expanded by remember { mutableStateOf(false) }
  Box { OutlinedButton(onClick={expanded=true},modifier=Modifier.fillMaxWidth()){Text("الصنف: $category")}; DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) { listOf("سجائر وتبغ","شوكولا وحلويات","علكة وبونبون","مشروبات ومياه","شيبس ومقرمشات","بسكويت وكيك","قهوة وشاي","مواد غذائية","مواد تنظيف","نظافة شخصية","لوازم الهاتف","بطاريات وولاعات","مثلجات","متنوعة").forEach { c -> DropdownMenuItem(text={Text(c)},onClick={category=c;expanded=false}) } } }
  OutlinedTextField(buy,{buy=it},label={Text("سعر الشراء")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
  OutlinedTextField(price,{price=it},label={Text("سعر البيع")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal),singleLine=true)
  OutlinedTextField(qty,{qty=it},label={Text("الكمية الحالية")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
  OutlinedTextField(minStock,{minStock=it},label={Text("حد التنبيه الأدنى")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number),singleLine=true)
  OutlinedTextField(barcode,{barcode=it},label={Text("الباركود (اختياري)")},singleLine=true)
 }},confirmButton={TextButton(onClick={val b=buy.toDoubleOrNull();val p=price.toDoubleOrNull();val n=qty.toIntOrNull();val min=minStock.toIntOrNull()
  if(name.isNotBlank()&&b!=null&&b>=0&&p!=null&&p>=0&&n!=null&&n>=0&&min!=null&&min>=0)scope.launch{val old=editing;val item=Product(id=old?.id?:0,name=name.trim(),buyPrice=b,sellPrice=p,stock=n,category=category,barcode=barcode.trim().ifBlank{null},minStock=min);if(old==null)add(item)else update(item);editing=null;name="";buy="";price="";qty="0";category="متنوعة";barcode="";minStock="5";addOpen=false;refresh()}else alert="تأكد من الاسم والأسعار والكمية وحد التنبيه"
 }){Text(if(editing==null)"حفظ" else "حفظ التعديلات")}},dismissButton={Row { if(editing!=null) TextButton(onClick={deleting=editing;addOpen=false}){Text("حذف",color=MaterialTheme.colorScheme.error)}; TextButton(onClick={addOpen=false}){Text("إلغاء")} }})
 if(deleting!=null) AlertDialog(onDismissRequest={deleting=null},title={Text("تأكيد حذف السلعة")},text={Text("هل تريد حذف "+(deleting?.name?:"")+"؟ لن تُحذف فواتيرها السابقة.")},confirmButton={TextButton(onClick={scope.launch{deleting?.let{remove(it)};deleting=null;refresh()}}){Text("حذف")}},dismissButton={TextButton(onClick={deleting=null}){Text("إلغاء")}})
 alert?.let{msg->AlertDialog(onDismissRequest={alert=null},confirmButton={TextButton(onClick={alert=null}){Text("حسناً")}},text={Text(msg)})}
}
