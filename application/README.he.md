[English](README.md) | עברית

<img src="icons_vector/app_icon.svg" alt="DroidRec תמונה" width="200"/>

# DroidRec
### רשם אנדרואיד מסך.

*לרשם משמע אתה/אתך צריך/צריכה אנדרואיד 10 או יותר מאוחר. לא רוט הכרחי.*

*(**זחירות**: לא כליהון מכשירים יכלים לרשם כליהון מקרים משמע או כלל)*

<img src="metadata/en-US/images/phoneScreenshots/1.jpg" alt="DroidRec צילם 1" width="300"/> <img src="metadata/en-US/images/phoneScreenshots/2.jpg" alt="DroidRec צילם 2" width="300"/>

[<img src="https://fdroid.gitlab.io/artwork/badge/get-it-on.png"
     alt="Get it on F-Droid"
     height="80">](https://f-droid.org/packages/com.yakovlevegor.DroidRec/)
[<img src="https://play.google.com/intl/en_us/badges/static/images/badges/en_badge_web_generic.png"
     alt="Get it on Google Play"
     height="80">](https://play.google.com/store/apps/details?id=com.yakovlevegor.DroidRec)

[רכש היישום](https://github.com/egorolegovichyakovlev/DroidRec/releases)

## בנוה
בנוה היישום פתח **gradle assembleRelease**

ברא מפתח

`keytool -genkeypair -keystore mykey.keystore -validity 365000 -keysize 4096 -keyalg RSA`

ושים בשם

`signature.keystore`.

(**עיון**: היי אחד נוסח מג'אווה)

אחרי פתח **quicksign.bash** וטענה נתיב אל מאנדראיד קבצים מלבנות

(*לא חשוב*) אלו אין תוכנות בבנוה מאנדראיד שקע הנתיב

"build-tools/*version*"

למשל:

`./quicksign.bash $ANDROID_HOME/build-tools/33.0.0`

## התעודה

#### The Unlicense
```
This is free and unencumbered software released into the public domain.

Anyone is free to copy, modify, publish, use, compile, sell, or
distribute this software, either in source code form or as a compiled
binary, for any purpose, commercial or non-commercial, and by any
means.

In jurisdictions that recognize copyright laws, the author or authors
of this software dedicate any and all copyright interest in the
software to the public domain. We make this dedication for the benefit
of the public at large and to the detriment of our heirs and
successors. We intend this dedication to be an overt act of
relinquishment in perpetuity of all present and future rights to this
software under copyright law.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
IN NO EVENT SHALL THE AUTHORS BE LIABLE FOR ANY CLAIM, DAMAGES OR
OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE,
ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR
OTHER DEALINGS IN THE SOFTWARE.

For more information, please refer to <http://unlicense.org/>
```
