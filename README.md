# بیکارم! — Android V1

یک fidget/toy طنز فارسی با محور «بیکاری» و فیزیک دو جسم آویزان. طراحی عمداً غیرجنسی و کارتونی-واقع‌گراست.

## امکانات V1
- دو جسم آویزان با فیزیک پاندولی، برخورد و chaos با ضربه‌های پشت‌سرهم
- لرزش (haptic) و صدای کوتاه کارتونی؛ حالت Office بی‌صداست
- شتاب‌سنج/جاذبه گوشی، با حساسیت بیشتر در Gravity Mode
- 6 مود: معمولی، Zen، Rage، Office، Turbo، Gravity
- 7 پوسته: کلاسیک، فوتبال، نارگیل، دیسکو، هندوانه، ماه، پینگ‌پنگ
- Boredom Meter و شمارنده امروز/کل
- Achievementها در 10 / 100 / 1000 / 10K / 100K ضربه
- اتفاق‌های مخفی در milestoneها و confetti
- Challenge روزانه با 3 الگوی چرخشی
- حالت دو نفره 10 ثانیه‌ای (هر نفر یک سمت)
- آمار ماندگار با SharedPreferences
- Share کردن اسکرین‌شات + متن رکورد از داخل اپ
- طراحی بدون dependency runtime و بدون AndroidX

## ساخت
پروژه را با Android Studio باز کنید و Android SDK 35 را نصب داشته باشید.

از ترمینال (با Gradle 8.9 و Android SDK 35):

```bash
gradle :app:assembleDebug
```

APK در مسیر زیر ساخته می‌شود:

`app/build/outputs/apk/debug/app-debug.apk`

## مشخصات فنی
- Java 17
- Android Gradle Plugin 8.7.3
- compileSdk / targetSdk: 35
- minSdk: 29
- package: `com.bikaram.toy`

## نکته طراحی
V1 هیچ تصویر آماده‌ای برای سوژه اصلی ندارد؛ تمام فرم‌ها، بافت، موهای پراکنده، پوسته‌ها و حرکت با Canvas رسم می‌شوند. بنابراین انیمیشن با فیزیک واقعی‌تر هماهنگ است و asset سنگینی لازم ندارد.
