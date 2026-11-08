package dz.kachier.app.data
// Schema v2 stores purchase cost per sold item for accurate gross-profit reporting.
import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Entity(tableName = "products")
data class Product(@PrimaryKey(autoGenerate = true) val id: Long = 0, val name: String, val buyPrice: Double = 0.0, val sellPrice: Double, val stock: Int = 0, val category: String = "متنوعة", val minStock: Int = 5, val barcode: String? = null)
@Entity(tableName = "sales")
data class Sale(@PrimaryKey(autoGenerate = true) val id: Long = 0, val total: Double, val createdAt: Long = System.currentTimeMillis())
@Entity(tableName = "sale_items")
data class SaleItem(@PrimaryKey(autoGenerate = true) val id: Long = 0, val saleId: Long, val productId: Long, val productName: String, val quantity: Int, val unitPrice: Double, val unitBuyPrice: Double = 0.0)
@Dao
interface ShopDao {
 @Query("SELECT * FROM products ORDER BY name COLLATE NOCASE") suspend fun getProducts(): List<Product>
 @Insert suspend fun addProduct(product: Product): Long
 @Update suspend fun updateProduct(product: Product)
 @Delete suspend fun deleteProduct(product: Product)
 @Insert suspend fun addSale(sale: Sale): Long
 @Insert suspend fun addItems(items: List<SaleItem>)
 @Query("SELECT COALESCE(SUM(total),0) FROM sales WHERE createdAt >= :start") suspend fun totalSince(start: Long): Double
 @Query("SELECT COALESCE(SUM((unitPrice-unitBuyPrice)*quantity),0) FROM sale_items INNER JOIN sales ON sale_items.saleId=sales.id WHERE sales.createdAt >= :start") suspend fun profitSince(start: Long): Double
}
@Database(entities = [Product::class, Sale::class, SaleItem::class], version = 3, exportSchema = false)
abstract class ShopDatabase : RoomDatabase() {
 abstract fun dao(): ShopDao
 companion object {
  @Volatile private var instance: ShopDatabase? = null
  private val MIGRATION_1_2 = object : Migration(1,2) {
   override fun migrate(db: SupportSQLiteDatabase) { db.execSQL("ALTER TABLE sale_items ADD COLUMN unitBuyPrice REAL NOT NULL DEFAULT 0.0") }
  }
  private val MIGRATION_2_3 = object : Migration(2,3) {
   override fun migrate(db: SupportSQLiteDatabase) {
    db.execSQL("ALTER TABLE products ADD COLUMN category TEXT NOT NULL DEFAULT 'متنوعة'")
    db.execSQL("ALTER TABLE products ADD COLUMN minStock INTEGER NOT NULL DEFAULT 5")
    db.execSQL("ALTER TABLE products ADD COLUMN barcode TEXT")
   }
  }
  fun get(context: Context): ShopDatabase = instance ?: synchronized(this) {
   instance ?: Room.databaseBuilder(context.applicationContext, ShopDatabase::class.java, "kachier-dz.db").addMigrations(MIGRATION_1_2, MIGRATION_2_3).build().also { instance = it }
  }
 }
}
