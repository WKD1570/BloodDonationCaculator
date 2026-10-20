# Google Play release kit

Everything prepared for publishing the app on Google Play, and what's left to do. The app ID is
`io.github.wkd1570.bdcalculator`. It can never change once the first bundle is uploaded.

| Item | Status |
|---|---|
| App icon 512×512 (`icon-512.png`, drawn by `tools/render_icons.py`) | Ready |
| Privacy policy (`docs/privacy-policy.md`) | Ready, needs publishing (step 4) |
| Store listing text and policy-form answers (below) | Drafted |
| Feature graphic 1024×500 | To do |
| Phone screenshots, 2–8 | To do. Use demo data, not your own records |
| Signed `.aab` | Built with the new upload key. Rebuild with `./gradlew bundleRelease` |

## Steps, in order

1. **Developer account.** Create one at [play.google.com/console](https://play.google.com/console): a one-time $25 fee plus identity verification, which can take a few days.
   A *personal* account created after November 13, 2023 must run a closed test with at least 12 testers who stay opted in for 14 days in a row before production unlocks (step 8). Older personal accounts and organization accounts skip that step.
2. **Upload key.** The upload key is `~/keystores/bdcalculator-upload.jks`, alias `upload`. It's kept outside the repo, and its password is only in the gitignored `keystore.properties`. Back up both the keystore file and the password, ideally in a password manager plus an offline copy. Never use the old `무제.jks`: it was committed in PR #1 and is still downloadable from that pull request's history, so treat it as public. On the first upload, Play App Signing creates the key that actually signs the app on users' phones, and registers this one as your upload key. If the upload key is ever lost or leaked, Play Console support can reset it.
3. **Build the bundle.** Run `./gradlew bundleRelease`. The signed bundle is at `app/build/outputs/bundle/release/app-release.aab`. Android Studio's *Build → Generate Signed App Bundle* does the same without `keystore.properties`. Release builds are shrunk with R8, and the keep rules for the bundled JSON files are in `app/src/main/keepRules/rules.keep`.
4. **Publish the privacy policy.** Merge to `main`, then in the GitHub repo go to *Settings → Pages* and choose *Deploy from a branch*, branch `main`, folder `/docs`. The policy appears at `https://wkd1570.github.io/BloodDonationCaculator/privacy-policy.html`. Open it in a browser to check before entering it in Play Console.
5. **Create the app** in Play Console: default language Korean, type app, free.
6. **Fill in App content** (*Policy → App content*) using the answers below.
7. **Fill in the store listing** with the text below, the icon, a feature graphic and screenshots. Category: Medical (or Health & Fitness). Contact email: p9131015@naver.com.
8. **Closed test.** Upload the `.aab` to the closed testing track, add at least 12 testers by their Google account emails, and share the opt-in link. Testers install from Play and stay opted in for 14 days. Google checks that they actually use the app, so ask them to open it a few times.
9. **Production.** Apply for production access in the Dashboard. You'll answer questions about how the test went. After approval, create a production release. Reviews usually take a few days.
10. **Every update** needs a higher `versionCode` in `app/build.gradle.kts` (and usually a new `versionName`).

## Store listing (ready to paste)

**앱 이름** (30자 이내): `헌혈 계산기 - 다음 헌혈일·연간 채혈량`
Alternative: `연간채혈량 계산기`, the title shown inside the app. The launcher label is still `BDCalculator` (`app_name` in `strings.xml`). Changing it to match is up to you.

**간단한 설명** (80자 이내):

```
헌혈 기록으로 다음 헌혈 가능일과 연간 채혈량을 계산하고, 헌혈의 집과 성분헌혈 기기를 찾아보세요.
```

**자세한 설명** (4000자 이내):

```
헌혈 기록을 남기면 다음 헌혈 가능일과 최근 1년 누적 채혈량을 계산해 주는 헌혈 도우미 앱입니다.

■ 현황
· 최근 1년 누적 채혈량을 연간 제한(2,160mL)과 비교해 보여줍니다.
· 전혈·혈장성분헌혈·혈소판성분헌혈마다 다음 헌혈 가능일과 D-day를 알려줍니다.
· 헌혈 캘린더에서 지난 헌혈과 헌혈 가능일을 한눈에 볼 수 있습니다.

■ 헌혈 기록
· 전혈·혈장·혈소판성분헌혈과 백혈구성분헌혈·조혈모세포 기증을 기록합니다.
· 헌혈증서 사진으로 헌혈일과 증서 번호 등을 채울 수 있습니다. 글자 인식은 기기 안에서 이루어집니다.

■ 의료 정보
· 헌혈 제한 약물을 검색하고 복용일을 넣으면 다음 헌혈 가능일에 반영합니다.
· 감염병 이력과 해외 방문·말라리아·vCJD 위험지역 체류를 입력하면 제한 기간을 계산합니다.
· Google 지도 타임라인 파일에서 위험지역 체류를 찾아 줍니다.

■ 헌혈의 집
· 전국 헌혈의 집을 지역별로 찾고 검색할 수 있습니다.
· 센터마다 성분헌혈 기기(혈장·혈소판, 기종별)를 보여주고, 혈소판 가능 여부나 기기 종류로 센터를 찾을 수 있습니다.
· 전화하기와 지도에서 보기(카카오맵)로 바로 연결됩니다.
· 마이페이지에서 자주 헌혈하는 지역을 정하면 그 지역 센터가 맨 위에 나옵니다.

■ 마이페이지
· 나이·체중·신장·성별로 헌혈 신체 기준을 확인하고 예측 혈량을 보여줍니다.

■ 개인정보
입력한 정보는 모두 기기에만 저장됩니다. 회원가입도 서버도 없습니다.

■ 꼭 읽어 주세요
· 이 앱은 개인이 만든 비공식 앱으로, 대한적십자사와 관련이 없습니다.
· 이 앱은 의료기기가 아니며, 질병을 진단·치료·치유·예방하지 않습니다. 실제 헌혈 가능 여부는 헌혈의 집 문진과 검사로 결정되니, 건강에 관한 판단은 의사 등 의료 전문가와 상담하세요.
· 헌혈 기준·약물·지역·기기 정보는 공개 자료를 바탕으로 정리했으며, 빠졌거나 바뀐 내용이 있을 수 있습니다.

자료 출처: 대한적십자사 공개 자료, 공공데이터포털 「대한적십자사_헌혈의집 정보」(2026.07.01 기준), 디시인사이드 헌혈 마이너 갤러리 「헌혈의집 성분채혈기기 현황」(헤마토필리아, 2026.10.14 기준)
문의: p9131015@naver.com
```

Before publishing, check the data rights:

- **Machine data.** The 성분헌혈 machine counts are transcribed from 헤마토필리아's post [「헌혈의집 성분채혈기기 현황 (26.10.14. 기준)」](https://gall.dcinside.com/mgallery/board/view/?id=blooddonation&no=79901) on DC Inside's 헌혈 마이너 갤러리. The post states no reuse terms, so ask the author for permission before release, by commenting on the post or messaging them in the gallery. Keep the credit, which is already in the 자료 출처 line above and under the app's 헌혈의 집 list. If they decline, take the machine data out of the app before releasing.
- **Center list.** The 공공데이터포털 dataset [「대한적십자사_헌혈의집 정보」](https://www.data.go.kr/data/15050729/fileData.do) is licensed 이용허락범위 제한 없음 (no restrictions). Attribution isn't required, but the app, this listing and the README cite it anyway.
- **Dates.** The machine data (2026.10.14) is newer than the center list (2026.07.01). 마곡역센터, 가산센터 and 정읍가두 have machine data but aren't in the July list, and they're probably new. The portal updates the dataset once a year, and its current file is still the 2026.07.01 one. To list newer centers before the next update, they'd have to be added by hand.

**첫 출시 출시 노트:**

```
첫 출시: 헌혈 기록·다음 헌혈 가능일·연간 채혈량, 헌혈 제한 약물과 감염병·체류 이력, 전국 헌혈의 집과 성분헌혈 기기 찾기
```

## App content answers

| Form | Answer |
|---|---|
| Privacy policy | `https://wkd1570.github.io/BloodDonationCaculator/privacy-policy.html` |
| Ads | No ads |
| App access | All functionality is available without special access (no login) |
| Content rating | Run the questionnaire: no violence, sexual content, profanity, gambling or user-to-user interaction. The app lists medications only as donation restrictions, which isn't drug use. Expect the lowest rating (전체이용가). |
| Target audience | 16–17 and 18 and over. Not designed for children, so the Families policy doesn't apply. |
| Health apps declaration | Declare health features. Pick the categories closest to a donation tracker and eligibility reference, and don't claim it's a medical device. |
| Government app / financial features / news app | No / none / no |
| Data safety | See below |

## Data safety form

Play counts data as *collected* only when it leaves the device. Everything the user enters (profile, records, medications, diseases, stays, photos, timeline files) stays on the phone, so none of it is collected. The one thing that does leave is from Google ML Kit, the certificate text recognition library ([ML Kit data disclosure](https://developers.google.com/ml-kit/android-data-disclosure)):

| Question | Answer |
|---|---|
| Does the app collect or share user data? | Yes, through the ML Kit SDK |
| Data type | App info and performance → **Diagnostics** (device manufacturer, model, OS version, the app's package name and version) |
| Collected / shared | Collected, **not shared**. ML Kit doesn't pass it to third parties. |
| Purpose | Analytics |
| Required or optional | Your call. It's only sent when the photo text recognition is used, which is optional. |
| Encrypted in transit | Yes (HTTPS) |
| Users can request deletion | The developer holds no data. Answer according to Play's wording for SDK-only diagnostics. |

## Permissions

| Permission | Why |
|---|---|
| `CAMERA` | Photographing a donation certificate. Only asked for when the user taps the camera option. |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Added by ML Kit for its diagnostics. The app's own features work offline. |

On Android 17, App info lists "Nearby devices" (local network access) for any app with `INTERNET`. The app doesn't request it.
