# Kachier DZ

تطبيق كاشير وإدارة حانوت على Android، مصمم للعمل دون اتصال بالإنترنت.

## المرحلة الأولى
- واجهة كاشير عربية.
- إضافة السلع مع سعر الشراء والبيع والكمية.
- سلة بيع وحساب المجموع.
- تسجيل المبيعات وتخفيض المخزون.
- قاعدة بيانات محلية Room.

## البناء
يتطلب JDK 17 وAndroid SDK وGradle 8.9.

```bash
gradle assembleDebug
```

ملف APK الناتج:
`app/build/outputs/apk/debug/app-debug.apk`

يمكن متابعة بناء APK تلقائياً من تبويب Actions في GitHub بعد كل Push إلى فرع main.
