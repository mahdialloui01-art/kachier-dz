package dz.kachier.app.data
import android.content.Context
import androidx.room.*

@Entity(tableName = "products")
data class Product(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val buyPrice: Double = 0.0, val sellPrice: Double, val stock: Int = 0)
@Entity(tableName = "sales")
data class Sale(@PrimaryKey(autoGenerate = true) val id: Long = 0, val total: Double, val createdAt: Long = System.currentTimeMillis())
@Entity(tableName = "sale_items")
data class SaleItem(@PrimaryKey(autoGenerate = true) val id: Long = 0, val saleId: Long, val productId: Long, val productName: String, val quantity: Int, val unitPrice: Double)
@Dao
interface ShopDao {
 @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE") suspend fun getProducts(): List<Product>
 @Insert suspend fun addProduct(product: Product): Long
 @Update suspend fun updateProduct(product: Product)
 @Insert suspend fun addSale(sale: Sale): Long
 @Insert suspend fun addItems(items: List<SaleItem>)
 @Query("SELECT COALESCE(SUM(total),0) FROM sales WHERE createdAt >= :start") suspend fun totalSince(start: Long): Double
}
@Database(entities = [Product::class, Sale::class, SaleItem::class], version = 1, exportSchema = false)
abstract class ShopDatabase : RoomDatabase() {
 abstract fun dao(): ShopDao
 companion object {
  @Volatile private var instance: ShopDatabase? = null
  fun get(context: Context): ShopDatabase = instance ?: synchronized(this) {
   instance ?: Room.databaseBuilder(context.applicationContext, ShopDatabase::class.java, "kachier-dz.db").build().also { instance = it }
  }
 }
}