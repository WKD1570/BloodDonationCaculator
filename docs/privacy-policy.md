# 개인정보처리방침 — 연간채혈량 계산기 (BDCalculator)

시행일: 2026년 10월 19일

연간채혈량 계산기(이하 "앱")는 개인 개발자 WKD1570이 비공식으로 만든 앱이며, 대한적십자사와 관련이 없습니다. 앱에는 회원가입도 서버도 없습니다. 앱에 입력한 정보는 사용자의 기기에만 저장되고, 개발자는 이 정보를 받거나 볼 수 없습니다.

## 1. 기기에 저장되는 정보

다음 정보는 사용자가 직접 입력한 경우에만 기기 안에 저장되며, 다음 헌혈 가능일·연간 채혈량·헌혈 자격을 계산하는 데만 쓰입니다.

- 프로필: 이름(선택), 생년월일, 성별, 체중·신장, 60~64세 헌혈 경험 여부, 자주 헌혈하는 지역
- 헌혈 기록: 헌혈 종류, 헌혈일, 채혈량, 헌혈증서 번호, 헌혈 장소
- 의료 정보: 헌혈 제한 약물과 복용일, 감염병 이력, 해외·위험지역 체류 이력
- 헌혈 계획: 계획한 헌혈의 종류와 날짜
- 앱 설정: 화면 테마

## 2. 카메라와 사진

- 헌혈 기록의 "사진으로 채우기"를 누를 때만 카메라를 쓰거나 사진을 고릅니다. 헌혈증서 글자 인식은 기기 안에서 이루어지며, 사진은 기기 밖으로 전송되지 않습니다.
- 앱 카메라로 찍은 사진은 앱의 임시 저장 공간(캐시)에 남을 수 있으며, 앱 데이터를 지우거나 앱을 삭제하면 함께 지워집니다.
- 갤러리에서 사진을 고를 때는 Android 사진 선택 도구를 쓰므로, 사용자가 고른 사진 한 장만 읽고 사진 보관함 전체에 접근하지 않습니다.

## 3. Google 지도 타임라인 파일

"Google 타임라인 가져오기"에서 사용자가 고른 파일은 기기 안에서만 읽어 말라리아·vCJD 위험지역 체류를 찾는 데 씁니다. 위치 기록 원본은 저장하지 않으며, 사용자가 저장하기로 한 체류 기록만 남습니다.

## 4. 기기 밖으로 전달되는 정보

- **글자 인식(Google ML Kit)**: 앱에 포함된 Google ML Kit는 진단과 사용 통계를 위해 기기 정보(제조사, 모델, OS 버전 등), 앱 패키지 이름과 버전을 Google에 보냅니다. 이 정보는 HTTPS로 암호화되어 전송되며, 사용자가 입력한 헌혈·건강 정보나 사진은 포함되지 않습니다. 자세한 내용은 [ML Kit 데이터 공개](https://developers.google.com/ml-kit/android-data-disclosure)에서 볼 수 있습니다.
- **전화하기·지도에서 보기**: 헌혈의 집 목록에서 이 버튼을 누르면 전화 앱이나 카카오맵(앱 또는 웹)이 열리면서 해당 헌혈의 집의 전화번호나 주소가 전달됩니다. 그 이후의 처리는 해당 앱과 서비스의 방침을 따릅니다.
- **Android 백업**: 기기에서 Google 백업이 켜져 있으면 Android가 앱 데이터(사진 캐시 제외)를 사용자의 Google 계정에 백업할 수 있습니다. 백업은 기기 설정에서 끌 수 있습니다.

개발자는 위 어느 경로로도 사용자의 정보를 받지 않습니다.

## 5. 보관과 삭제

모든 정보는 사용자의 기기에 있습니다. 각 기록은 앱 안에서 지울 수 있고, 기기의 설정 > 애플리케이션에서 앱 데이터를 삭제하거나 앱을 삭제하면 모든 정보가 지워집니다. 개발자는 정보를 보관하지 않으므로 별도로 삭제를 요청할 필요가 없습니다.

## 6. 아동

이 앱은 헌혈할 수 있는 나이(만 16세 이상)의 사용자를 위한 것이며, 만 14세 미만 아동의 개인정보를 알면서 수집하지 않습니다.

## 7. 문의

개인정보 관련 문의: p9131015@naver.com

## 8. 변경

이 방침이 바뀌면 이 페이지에 새 시행일과 함께 게시합니다.

---

# Privacy Policy — BDCalculator (연간채혈량 계산기)

Effective: October 19, 2026

BDCalculator ("the app") is an unofficial app made by the independent developer WKD1570 and is not affiliated with the Korean Red Cross. The app has no accounts and no server. What you enter stays on your device; the developer never receives or sees it.

**Stored on your device only**, when you enter it, and used only to calculate your next eligible donation dates, annual donation volume and eligibility: your profile (optional name, birth date, sex, weight and height, whether you donated at 60–64, your usual donation region), donation records (type, date, volume, certificate number, place), medical information (restricted medications and dates, infectious disease history, travel and stays in risk areas), planned donations (type and date) and the theme setting.

**Camera and photos.** The camera or a photo is used only when you tap "사진으로 채우기" (fill from photo). Text recognition runs on the device and photos are never sent off it. Photos taken with the in-app camera may remain in the app's cache until you clear the app's data or uninstall it. Photos from your gallery are chosen through Android's photo picker, so the app reads only the photo you pick.

**Google Maps Timeline files** you choose are read on the device only, to find stays in malaria or vCJD risk areas. The raw location history is not stored; only the stays you choose to save are.

**What leaves the device.**
- Google ML Kit, used for text recognition, sends device information (manufacturer, model, OS version and similar), the app's package name and version to Google for diagnostics and usage statistics, encrypted over HTTPS. It does not include anything you enter or your photos. See [ML Kit's data disclosure](https://developers.google.com/ml-kit/android-data-disclosure).
- "Call" and "Show on map" open your phone app or Kakao Map (app or web) with that donation center's phone number or address; those apps' own policies apply from there.
- If Google backup is on, Android may back up the app's data (excluding the photo cache) to your Google account. You can turn backup off in your device settings.

The developer receives none of this.

**Retention and deletion.** Everything is on your device. You can delete individual records in the app, or delete all of it by clearing the app's data or uninstalling the app. The developer holds no data, so there is nothing to request deletion of.

**Children.** The app is meant for people old enough to donate blood (16 and over) and does not knowingly collect information from children under 14.

**Contact:** p9131015@naver.com

**Changes** to this policy will be posted on this page with a new effective date.
