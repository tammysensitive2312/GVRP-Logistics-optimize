# UI design — DRIVER-APP-001 (Flutter driver app, MVP)

Status: **ACCEPTED** by the user on 2026-10-28 ("duyệt bắt đầu từ bước 2"), including the five decisions listed for review in the conversation:
- zone banner in warning colour;
- "Người gửi" line on each list row;
- tapping a stop row centres the map only;
- superseded mode hides the leg buttons;
- no dark mode, tablet or landscape layout.

Written 2026-10-28 at the user's request ("thiết kế giao diện trước và ghi vào work-items").

**Revision 2 (2026-10-31)** at the user's request ("cải thiện UI, maps có thể dùng google maps giống bản web … lấy theo phong cách bản web"); see `findings-ui-rev2.md`. Section 8 below **supersedes** the colour tokens of §3, the map drawing table of §4.4 and the list banner colour of §4.3. Everything else in §1–§7 still applies.

Relation to `design.md`:
- This file refines the screen specification in `design.md` §5–§7 into layouts, states, tokens and strings.
- It does not change behaviour, the API contract, or the test table of `design.md` §8.
- Where the two conflict, `design.md` wins and the conflict must be reported.
- `design.md` is not edited, because it is hashed evidence of the run.

Labels:
- **[§5]** = text taken verbatim from `design.md`.
- **[ui]** = new in this file (wording, layout, colour, states not spelled out in `design.md`).

All sample data in this file is invented. No real customer, branch, plate, phone or coordinate.

---

## 1. Principles for the driver UI

1. **Glanceable while stopped.**
   - One primary action per screen, at the bottom, within thumb reach.
   - Large text for the next stop and the current step.
2. **The path on the map is the instruction.**
   - Server geometry is drawn strongly.
   - Anything approximate is visibly dashed and labelled.
   - There is no control that leaves the app for navigation (DA-R08).
3. **Status is never only a colour.**
   - Every coloured banner also carries text.
   - Every map marker type also differs in shape (accessibility, sunlight glare).
4. **Errors keep the driver in place.**
   - A failed call shows a message and "Thử lại" where the content would be.
   - The rest of the screen stays usable.
   - Only a 401 navigates away, back to Login.

## 2. Navigation map [ui]

```
App start
  └─ AuthGate ── release build without API_BASE_URL ──> ConfigErrorScreen (no network call)
        ├─ no token ───────────────> LoginScreen
        └─ token in secure storage ─> RoutesScreen (My routes)
                                         └─ tap row ─> RouteDetailScreen(sendId)
                                                         └─ "Xem tất cả bước" ─> StepsSheet (modal bottom sheet)

Any screen ── 401 ──> token cleared ──> LoginScreen with the expiry message
App bar "Đăng xuất" (RoutesScreen, RouteDetailScreen) ──> token cleared ──> LoginScreen
```

Implementation notes:
- Use plain `Navigator` / `MaterialPageRoute`; no router package is needed.
- `AuthGate` in `app.dart` decides the screen from the auth state.
- `RouteDetailScreen` is pushed with `sendId` only.
- Data is fetched again on every open; nothing is cached on disk.

## 3. Visual tokens [ui]

Theme: Material 3 with `useMaterial3: true` and light mode only for the MVP. Dark mode is out of scope; record it if requested.

| Token | Value | Use |
|---|---|---|
| Seed colour | `Color(0xFF0F6E56)` (deep teal) | `ColorScheme.fromSeed`; primary buttons, current leg on the map |
| Danger | `colorScheme.error` / `errorContainer` | Superseded banner, form errors |
| Warning | `Color(0xFFFAEEDA)` background, `Color(0xFF633806)` text | Zone banner, "newer version" banner on the list |
| Neutral path | `Color(0xFF888780)` | Non-current legs, dashed fallback legs |
| My position | `Color(0xFF185FA5)` dot, 20 % halo | Driver location |
| Zone ring | `Color(0xFFA32D2D)`, 3 dp | Border of stop markers that have `zone_names` |

Spacing and type:
- Spacing scale: 4 / 8 / 12 / 16 / 24 dp. Screen padding is 16 dp. Card padding is 12 dp; cards use 12 dp radius.
- Font: platform default (Roboto), which covers Vietnamese diacritics.
- Sizes: body 16 sp; the next-stop name and the current step text are 20 sp, weight 500.
- Touch targets are at least 48 × 48 dp.
- The primary action button is full width and 56 dp high.
- Layouts must not overflow at text scale 1.3 (system font size "large").

## 4. Screens

### 4.1 ConfigErrorScreen [ui]

Shown only in a release build started without `--dart-define=API_BASE_URL` (design §6). It makes no network call.

```
┌──────────────────────────────┐
│                              │
│        (!) icon 48dp         │
│  Ứng dụng chưa được cấu hình │
│  địa chỉ máy chủ.            │
│  Liên hệ quản trị viên.      │
│                              │
└──────────────────────────────┘
```

### 4.2 LoginScreen (DA-R01, design §5.1)

```
┌──────────────────────────────┐
│ GVRP Tài xế                  │  app bar, no actions
├──────────────────────────────┤
│ [expiry banner, if any]      │  errorContainer, text below
│                              │
│ Đăng nhập                    │  title 22sp
│ ┌──────────────────────────┐ │
│ │ Chi nhánh                │ │  TextField, autofill off
│ └──────────────────────────┘ │
│ ┌──────────────────────────┐ │
│ │ Tên đăng nhập            │ │
│ └──────────────────────────┘ │
│ ┌──────────────────────────┐ │
│ │ Mật khẩu            (eye)│ │  obscured, toggle visibility
│ └──────────────────────────┘ │
│ error text (error colour)    │  one line, under the fields
│                              │
│ ┌──────────────────────────┐ │
│ │        Đăng nhập         │ │  FilledButton, 56dp, full width
│ └──────────────────────────┘ │
└──────────────────────────────┘
```

States:

| State | Shown |
|---|---|
| Idle | Form; the button is enabled once all three fields are non-empty after trim |
| Submitting | Button shows a 20 dp spinner and is disabled; fields are read-only |
| Not a driver | Error text **[§5]** "Tài khoản này không phải tài xế." Nothing is stored. |
| 400 from the API | Error text **[§5]** "Sai chi nhánh, tên đăng nhập hoặc mật khẩu." |
| 5xx / timeout / no connection | Error text **[§5 §6]** "Không kết nối được máy chủ". The button stays as the retry. |
| Arrived after a 401 | Banner **[§5.4]** "Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại." |

Behaviour:
- The keyboard "next" action moves between fields; "done" on the password submits.
- The password field is cleared after any failed attempt; the branch and username fields are kept.

Known gap: by static reading, a wrong password currently returns **500** from the backend (`BadCredentialsException` reaches the generic handler), so it shows the "Không kết nối được máy chủ" message. This is NOT_VERIFIED and is listed for `implementation.md` § Remaining Uncertainty. The UI follows design §6 as written.

### 4.3 RoutesScreen — My routes (DA-R02, design §5.2)

```
┌──────────────────────────────┐
│ Tuyến của tôi        [logout]│
├──────────────────────────────┤
│ ┌──────────────────────────┐ │
│ │ Có phiên bản tuyến mới   │ │  warning banner, only if any
│ │ hơn — tuyến hiện hành nằm│ │  SUPERSEDED exists [§5]
│ │ ở đầu danh sách          │ │
│ └──────────────────────────┘ │
│ ┌──────────────────────────┐ │
│ │ 29X-000.00          (>)  │ │  CURRENT row
│ │ Gửi lúc 27/10/2026 08:05 │ │
│ │ Người gửi: Điều phối A   │ │
│ └──────────────────────────┘ │
│ ┌──────────────────────────┐ │
│ │ 29X-000.00 [Phiên bản cũ]│ │  SUPERSEDED row: muted text,
│ │ Gửi lúc 27/10/2026 07:40 │ │  neutral chip [§5]
│ └──────────────────────────┘ │
└──────────────────────────────┘
```

Row content:
- Licence plate: 18 sp, weight 500.
- **[§5]** "Gửi lúc dd/MM/yyyy HH:mm".
- **[ui]** "Người gửi: {sender_name}" in secondary text.
- The whole row is the tap target.

Order: server order, without re-sorting (CURRENT first).

States:

| State | Shown |
|---|---|
| Loading (first) | Centred `CircularProgressIndicator` |
| Data | List inside `RefreshIndicator` (pull to refresh **[§5]**) |
| Empty | Icon plus **[§5]** "Chưa có tuyến nào được giao.", still pull-to-refresh |
| Error | Message from the §6 table plus an outlined "Thử lại" button, in place of the list |
| Refresh error | Keep the old list and show a SnackBar with the message |

Time display: `sent_at` is a `LocalDateTime` with no zone (server local time). Format it as-is with `dd/MM/yyyy HH:mm`. Do **not** convert time zones.

### 4.4 RouteDetailScreen (DA-R03…R07, design §5.3)

Layout:
- The map fills the screen behind a `DraggableScrollableSheet`.
- Initial sheet size is 0.5, minimum 0.25, maximum 0.9.
- So the map takes the top half at start, as design §5.3 specifies.

```
┌──────────────────────────────┐
│ ← 29X-000.00         [logout]│
├──────────────────────────────┤
│            MAP               │
│   ■ depot   ①──②- - -③      │  solid = server geometry,
│        ● me                  │  dashed = fallback
│  ┌────────────────────────┐  │
│  │- - - Đường đi chưa có  │  │  legend chip, only when any
│  │từ máy chủ (chỉ minh hoạ)│ │  leg uses the fallback [§5]
│  └────────────────────────┘  │
│                  [◎ Theo dõi]│  follow-me toggle (FAB small)
├─────────── handle ───────────┤
│ [SUPERSEDED banner]          │  1. red, see below
│ [zone banner]                │  2. warning
│ ┌──────────────────────────┐ │
│ │ Hướng dẫn · Bước 1/4     │ │  3. guidance card
│ │ Rẽ phải vào phố A        │ │  20sp
│ │ 350 m · phố A            │ │
│ │ Xem tất cả bước          │ │  TextButton → StepsSheet
│ └──────────────────────────┘ │
│ ┌──────────────────────────┐ │
│ │ Điểm tiếp theo · 2/5     │ │  4. next-stop card
│ │ Cửa hàng B        09:30  │ │  20sp name, arrival HH:mm
│ │ Số 1 phố C, quận D       │ │  address
│ │ Khách hàng B             │ │
│ │ 📞 09xx xxx xxx          │ │  tap → tel: (DA-R03)
│ │ Ghi chú: gọi trước 10ph  │ │  delivery_notes
│ │ [Vùng: Vùng X]           │ │  chip, only with zone_names
│ │ ┌─────────┐┌───────────┐ │ │
│ │ │ Quay lại││Đã đến điểm│ │ │  outlined + filled 56dp
│ │ └─────────┘│   này     │ │ │
│ │            └───────────┘ │ │
│ └──────────────────────────┘ │
│ Tất cả điểm dừng (5)    ˅    │  5. ExpansionTile
└──────────────────────────────┘
```

The phone line uses a phone icon, not an emoji (the emoji above is only for the sketch).

**Banners** (in this order, only when applicable):

| Banner | Condition | Style | Text |
|---|---|---|---|
| Superseded | `status == SUPERSEDED` | `errorContainer`, icon `warning` | **[§5]** "Đây là phiên bản cũ — không đi theo tuyến này" |
| Zone | any stop has non-empty `zone_names` | warning token, icon `shield` | **[§5]** "Tuyến có vùng hạn chế — đi đúng đường trên bản đồ" |

**Superseded mode [§5]:**
- Guidance is hidden: the card is not built at all.
- The leg buttons are hidden **[ui]**.
- The map and the stop list remain, read-only.

**Leg progression [§5]:**
- `legIndex` starts at 0, meaning start depot → first stop.
- The next stop is `stops[legIndex + 1]`.
- "Đã đến điểm này" increments `legIndex`. It is hidden when the next stop is the end depot and that depot has been reached.
- "Quay lại" decrements `legIndex` and is disabled at 0.
- The state is local, held in a `Notifier` keyed by `sendId`. It is lost when the screen closes.

**Guidance card [§5]:**
- Shows the instructions of the current leg: `stops[legIndex].instructions_to_next`.
- Shows step 1, the counter "Bước 1/n" and "Xem tất cả bước".
- Distance: under 1000 m it reads "{m} m"; otherwise "{km:0.0} km" **[ui]**.
- When there are no instructions it reads **[§5]** "Chưa có hướng dẫn từ máy chủ cho chặng này."

**StepsSheet [ui]:**
- A modal bottom sheet with a numbered list of all steps of the current leg: text, then distance and street in secondary text.
- It is read-only and has no navigation hand-off.

**Next-stop card:**
- Missing optional fields (customer, phone, notes) are omitted. No blank labels.
- The phone line is shown only when `customer_phone` is non-empty.
- Tapping the phone calls `launchUrl(Uri(scheme: 'tel', path: phone))`. Nothing else (DA-R08, DT-11).
- Depot stops show the depot name with a "Kho" chip and no customer block **[ui]**.

**All stops list:**
- One row per stop: plan number, name, arrival `HH:mm`, and the zone chip when present.
- Rows are coloured by state:
  - stops already passed (index ≤ `legIndex`) are muted, with a check icon;
  - the next stop is highlighted.
- Tapping a row centres the map on that stop **[ui]**. It does not change `legIndex`.

**Map [§5]:**

| Element | Drawing |
|---|---|
| Tiles | OSM public tiles with `userAgentPackageName: 'org.truong.gvrp_driver'`; attribution text "© OpenStreetMap" bottom-left (tile policy) |
| Leg with `geometry_to_next` | Solid polyline, 5 dp, seed colour when it is the current leg; otherwise 3 dp neutral path colour |
| Leg without geometry | Straight `StrokePattern.dashed(segments: [10, 6])`, 3 dp neutral; legend chip is shown |
| Stop marker | 28 dp circle, number = plan order, white text |
| Depot marker | 28 dp rounded square, dark neutral, warehouse icon (shape differs from stops) |
| Zone stop | Stop marker plus a 3 dp zone-ring border |
| Passed stops | Marker at 50 % opacity |
| My position | 14 dp blue dot plus a 20 % halo; only after permission |

Camera:
- On open, fit the bounds of all stops with 48 dp padding.
- With follow-me on, centre on each new position and keep the zoom.
- Any manual pan turns follow-me off **[ui]**.

States:

| State | Shown |
|---|---|
| Loading | Map area grey, centred spinner; sheet hidden |
| Data | As drawn above |
| 403 | Full-screen message **[§6]** "Bạn không có quyền xem tuyến này." plus a "Quay lại" button to the list |
| 400 `RESOURCE_NOT_FOUND` | **[§6]** "Không tìm thấy tuyến." plus "Quay lại" |
| Network / 5xx | **[§6]** "Không kết nối được máy chủ" plus "Thử lại" |

### 4.5 Location permission (DA-R07, design §5.3)

Flow:
1. The driver turns on "Theo dõi vị trí" for the first time.
2. The in-app explanation dialog opens.
3. "Tiếp tục" opens the OS prompt.

```
┌──────────────────────────────┐
│ Hiển thị vị trí của bạn      │  [ui] title
│                              │
│ Vị trí chỉ hiển thị trên bản │  [§5] body
│ đồ của bạn, không gửi về máy │
│ chủ.                         │
│                              │
│          [Để sau] [Tiếp tục] │  [ui] TextButton, FilledButton
└──────────────────────────────┘
```

- If the OS denies the permission, the toggle turns off.
- A SnackBar then shows **[ui]** "Chưa có quyền vị trí. Bạn có thể bật trong Cài đặt của máy." The map keeps working.
- If the denial is permanent, the SnackBar action "Mở cài đặt" calls `Geolocator.openAppSettings()`. That opens the app's own settings page, not a navigation app.
- The position is never sent to the server and never logged.

## 5. Strings (`lib/l10n/strings_vi.dart`)

Keep every UI string here as a `static const` on one class, for example `S.loginTitle`. Strings marked [§5]/[§6] must match `design.md` exactly.

| Key | Text | Source |
|---|---|---|
| `appTitle` | GVRP Tài xế | [ui] |
| `configError` | Ứng dụng chưa được cấu hình địa chỉ máy chủ. Liên hệ quản trị viên. | [ui] |
| `loginTitle` | Đăng nhập | [§5] |
| `branchName` | Chi nhánh | [§5] |
| `username` | Tên đăng nhập | [§5] |
| `password` | Mật khẩu | [§5] |
| `loginButton` | Đăng nhập | [§5] |
| `notDriver` | Tài khoản này không phải tài xế. | [§5] |
| `badCredentials` | Sai chi nhánh, tên đăng nhập hoặc mật khẩu. | [§5] |
| `sessionExpired` | Phiên đăng nhập đã hết hạn, vui lòng đăng nhập lại. | [§5] |
| `logout` | Đăng xuất | [ui] |
| `myRoutes` | Tuyến của tôi | [ui] (screen named "My routes" in §5) |
| `sentAt` | Gửi lúc {dd/MM/yyyy HH:mm} | [§5] |
| `sender` | Người gửi: {name} | [ui] |
| `supersededTag` | Phiên bản cũ | [§5] |
| `newerVersionBanner` | Có phiên bản tuyến mới hơn — tuyến hiện hành nằm ở đầu danh sách | [§5] |
| `noRoutes` | Chưa có tuyến nào được giao. | [§5] |
| `supersededBanner` | Đây là phiên bản cũ — không đi theo tuyến này | [§5] |
| `zoneBanner` | Tuyến có vùng hạn chế — đi đúng đường trên bản đồ | [§5] |
| `guidanceTitle` | Hướng dẫn | [ui] |
| `stepCounter` | Bước {i}/{n} | [§5] |
| `allSteps` | Xem tất cả bước | [§5] |
| `noGuidance` | Chưa có hướng dẫn từ máy chủ cho chặng này. | [§5] |
| `nextStop` | Điểm tiếp theo | [ui] |
| `arrived` | Đã đến điểm này | [§5] |
| `back` | Quay lại | [§5] |
| `allStops` | Tất cả điểm dừng ({n}) | [§5] |
| `depotChip` | Kho | [ui] |
| `zoneChip` | Vùng: {names} | [ui] |
| `notesPrefix` | Ghi chú: | [ui] |
| `fallbackLegend` | Đường đi chưa có từ máy chủ (chỉ minh hoạ) | [§5] |
| `followMe` | Theo dõi vị trí | [§5] |
| `locationDialogTitle` | Hiển thị vị trí của bạn | [ui] |
| `locationExplain` | Vị trí chỉ hiển thị trên bản đồ của bạn, không gửi về máy chủ | [§5] |
| `later` | Để sau | [ui] |
| `continueLabel` | Tiếp tục | [ui] |
| `locationDenied` | Chưa có quyền vị trí. Bạn có thể bật trong Cài đặt của máy. | [ui] |
| `openSettings` | Mở cài đặt | [ui] |
| `forbidden` | Bạn không có quyền xem tuyến này. | [§6] |
| `notFound` | Không tìm thấy tuyến. | [§6] |
| `network` | Không kết nối được máy chủ | [§6] |
| `clientError` | Yêu cầu không hợp lệ. Vui lòng thử lại. | [ui] (§6 "generic Vietnamese message") |
| `retry` | Thử lại | [§6] |

## 6. Widget keys for tests [ui]

Stable `Key`s let the DT widget tests find elements without matching on text:

| Key | Widget | Used by |
|---|---|---|
| `login.submit` | Login button | DT-03, DT-04 |
| `routes.newerBanner` | List banner | DT-06 |
| `routes.row.{id}` | List row | DT-06 |
| `detail.supersededBanner` | Red banner | DT-07 |
| `detail.zoneBanner` | Zone banner | DT-10 |
| `detail.guidance` | Guidance card | DT-07 (absent), DT-09 |
| `detail.arrived` | "Đã đến điểm này" | DT-09 |
| `detail.phone` | Phone line | DT-11 |
| `map.fallbackLegend` | Legend chip | DT-08 |
| `map.stop.{index}` | Stop marker | DT-10 |

## 7. Out of scope for this UI

These are excluded by `request.md` §4 or are not needed for the MVP:
- dark mode;
- tablet layout;
- landscape-specific layout;
- offline banner;
- delivery confirmation buttons;
- push notifications;
- any "open in Maps" control.

## 8. Revision 2 — web-client style and Google tiles (2026-10-31)

Source: user request of 2026-10-31 and the decision "Tile Google như web" (`findings-ui-rev2.md`). Reference: `GVRP_Entry_client_v2` driver view (`driver-view.component.*`), login (`login.component.*`) and global tokens (`src/styles.scss`).

### 8.1 Tokens (replace §3 colours)

| Token | Value | Use (web origin) |
|---|---|---|
| Primary | `#1976D2` | Seed and primary; stop pins, route lines, labels (`driver-view` pins and polyline) |
| Primary light | `#E3F2FD` | Guidance box, info banner, badges (`.guide-current`, `.driver-banner`) |
| Next-stop card | `#F5F9FF` background, `#BBDEFB` border | `.next-stop` |
| Outline accent | `#4A90E2` | Outlined buttons (`.btn-logout`, `.btn-toggle`) |
| Login header | gradient `#4A90E2` → `#357ABD` | `.login-left` |
| Depot pin | `#0B8043` | Web depot pin |
| Background | `#F8F9FA` | `$bg-light` |
| Border | `#E5E5E5` (inputs), `#DDDDDD` (cards) | `$border-color`, `.driver-route` |
| Text | `#333333` main, `#666666` secondary | `$text-dark`, `$text-gray` |
| Danger text | `#B42318` | `.driver-flag` |
| Warning, zone ring, my position | unchanged from §3 | — |

Shapes: cards 8 dp radius, 1 dp border, no elevation; inputs filled `#F8F9FA`, 1.5 dp border, 8 dp radius, primary border on focus; buttons 8 dp radius. Touch targets, the 56 dp primary button and text-scale rules of §3 stay.

### 8.2 Screens

- **Login:**
  - a blue gradient header with a truck icon, the title "GVRP Tài xế" and the subtitle "Xem và đi theo tuyến được giao" [ui];
  - below it, a white form card;
  - fields with leading icons: branch (business), username (person), password (lock).
  - States and texts are as in §4.2.
- **App bar** (list and detail):
  - white, dark text, 1 dp bottom border;
  - logout is an outlined "Đăng xuất" button (web `.btn-logout`).
- **My routes:**
  - each route is a white card with a 1 dp border;
  - truck icon, plate 17 sp bold, then meta lines with icons: schedule "Gửi lúc …", person "Người gửi: …";
  - a SUPERSEDED row shows the label "Phiên bản cũ" in danger text on `#FDECEA`;
  - the "newer version" banner uses primary light (web `.driver-banner`), not the warning colour.
- **Route detail sheet** (initial size 0.45, min 0.2, max 0.9):
  - Banners as in §4.4.
  - Guidance section:
    - header row: navigation icon, "Hướng dẫn", and on the right an outlined "Xem tất cả bước" (opens the StepsSheet);
    - below, a primary-light box with a 40 dp primary turn icon on the left, then the label "TIẾP THEO", the step text (18 sp, bold), "{distance} · {street}" in secondary text, and "Bước 1/n";
    - the turn icon is chosen from `sign`, assuming GraphHopper codes (−3/−2 left, −1 slight left, 1 slight right, 2/3 right, 4 finish, 6 roundabout, otherwise straight); the text stays the instruction, the icon only adds a shape (NOT_VERIFIED against P1b output);
    - no instructions: the same box with "Chưa có hướng dẫn từ máy chủ cho chặng này."
  - Next-stop card:
    - `#F5F9FF` background with a `#BBDEFB` border;
    - label "ĐIỂM TIẾP THEO · i/n" (uppercase, primary, 12 sp, bold);
    - name 20 sp bold with a badge "Kho" or "Đơn hàng";
    - meta lines with icons: schedule (arrival `HH:mm`), place (address), person (customer), phone (tap → `tel:`), notes;
    - then the zone chip and the leg buttons as in §4.4.
  - "Tất cả điểm dừng (n)": rows show a pin in the map style (blue number, green depot), name, arrival, zone chip; passed rows are muted with a check.

### 8.3 Map (replaces the §4.4 map table)

| Element | Drawing |
|---|---|
| Tiles | `https://mt1.google.com/vt/lyrs=m&x={x}&y={y}&z={z}`, max zoom 20, attribution "© Google" bottom-left. Same source as the web client. **Dev/test only, see 8.5** |
| Leg with `geometry_to_next` | Solid primary line; current leg 6 dp opaque, other legs 4 dp at 50 % opacity |
| Leg without geometry | Straight dashed line `[10, 10]`, 4 dp primary at 60 % opacity (current) or 40 % (others); the legend chip is shown |
| Direction | A small arrow at the middle of each leg, pointing along it (web `polylineDecorator`) |
| Stop marker | 34 dp primary circle, 3 dp white border, shadow, white number |
| Depot marker | 34 dp `#0B8043` rounded square (shape differs, §1.3), white border, warehouse icon |
| Zone stop | Same marker with a 3 dp zone-ring border instead of white |
| Passed stops | 50 % opacity |
| My position | Unchanged |

Map controls, a column at the top-right of the map with white 44 dp buttons, rounded, with a shadow:
1. "+": zoom in one level (`zoomIn`, "Phóng to");
2. "−": zoom out one level (`zoomOut`, "Thu nhỏ");
3. fit: show all stops again (`fitRoute`, "Xem cả tuyến"); this turns follow-me off;
4. follow-me toggle, as before.

Zoom stays between 3 and 20 and keeps the map centre. Pinch, double-tap and drag gestures stay enabled; rotation stays disabled.

### 8.4 Strings added [ui]
`loginSubtitle` "Xem và đi theo tuyến được giao", `nextLabel` "Tiếp theo", `nextStopLabel` "Điểm tiếp theo" (existing `nextStop`, shown uppercase), `orderBadge` "Đơn hàng", `zoomIn` "Phóng to", `zoomOut` "Thu nhỏ", `fitRoute` "Xem cả tuyến", `googleAttribution` "© Google". All §5 strings marked [§5]/[§6] are unchanged.

### 8.5 Release blocker
The Google tile endpoint is undocumented, and using it outside Google's SDKs or APIs is against the Google Maps Platform terms, as it already is in the web client. Before any release, it must be replaced by a licensed tile source or by the official SDK with an API key. The user accepted this for development and testing on 2026-10-31.
