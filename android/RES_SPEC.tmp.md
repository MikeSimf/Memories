# RES_SPEC — общий контракт ресурсов (временный служебный файл)

Каталог ресурсов: `/data/external/dnd-memory/android/app/src/main/res/`
Пакет: `ru.dndmemory`. ViewBinding включён: `activity_auth.xml` → `ActivityAuthBinding`,
`fragment_entity_list.xml` → `FragmentEntityListBinding`, `item_entity.xml` → `ItemEntityBinding`,
`dialog_member_edit.xml` → `DialogMemberEditBinding`.

## Жёсткие правила

1. Создавать ТОЛЬКО файлы `res/layout/*.xml` из списка ниже. Не создавать и не менять
   `strings.xml`, `colors.xml`, `themes.xml`, `AndroidManifest.xml`, `drawable/*`, `mipmap/*` —
   их делает заказчик задачи.
2. Ссылаться на строки ТОЛЬКО через `@string/имя` из раздела «Строки». Других `@string/` быть не должно.
3. Иконки — ТОЛЬКО `@drawable/имя` из раздела «Drawable».
4. Цвета — ТОЛЬКО `@color/имя` из раздела «Цвета».
5. Каждый `android:id` — ровно тот, что указан в спецификации файла (ни больше, ни меньше).
6. Корневой элемент и типы View — как указано. Атрибут `tools:` использовать можно,
   namespace `xmlns:tools="http://schemas.android.com/tools"` объявлять там, где нужен.
7. Никаких `android:text` литералами — только `@string/...` (кроме `tools:text`, он разрешён).
8. Все размеры — в `dp`/`sp`. Ориентация — вертикальная, интерфейс для телефона.
9. Оформление: Material Components (`com.google.android.material.*`), карточки —
   `com.google.android.material.card.MaterialCardView`, кнопки — `com.google.android.material.button.MaterialButton`,
   текстовые поля — `com.google.android.material.textfield.TextInputLayout` +
   `com.google.android.material.textfield.TextInputEditText`.
10. Каждый layout должен быть самодостаточным (никаких `<include>` на несуществующие файлы).
11. Единственная разрешённая «библиотечная» строка — `@string/appbar_scrolling_view_behavior`
    (стандартный behavior для CoordinatorLayout).
12. Не задавать `style="@style/..."` на кнопках и полях; допустимы только `style="?attr/..."`.

## Строки (полный список; другие использовать нельзя)

app_name, action_retry, action_cancel, action_save, action_delete, action_edit, action_close,
action_create, action_refresh, action_search, action_settings, action_logout, action_invite,
action_scan_qr, action_sync_now, action_full_resync, action_publish, action_save_draft,
action_preview, action_edit_body, action_add, action_revoke, action_accept, action_accept_login,
action_copy, action_share, action_overwrite, action_take_server, ok,
sync_status_idle, sync_status_syncing, sync_status_offline, sync_status_error, sync_status_conflict,
sync_last_at, sync_never,
error_network, error_unauthorized, error_forbidden, error_not_found, error_conflict,
error_rate_limited, error_unknown, error_email_taken, error_invite_invalid, error_server,
error_password_short, error_required, error_api,
auth_title, auth_tab_login, auth_tab_register, auth_email, auth_password, auth_display_name,
auth_login, auth_register, auth_or_oauth, auth_oauth_yandex, auth_oauth_vk, auth_invite_pending,
auth_welcome,
campaign_list_title, campaign_list_empty, campaign_list_empty_hint, campaign_list_new,
campaign_list_signed_as, campaign_edit_title_new, campaign_edit_title_edit, campaign_field_title,
campaign_field_description, campaign_counts, campaign_updated, campaign_delete_confirm,
role_owner, role_editor_master, role_editor_hero, role_viewer,
tab_heroes, tab_npcs, tab_locations, tab_items, tab_retellings, tab_members,
entity_type_hero, entity_type_npc, entity_type_location, entity_type_item,
entity_list_empty, entity_list_add, entity_detail_title, entity_detail_type, entity_detail_author,
entity_detail_updated, entity_detail_tags, entity_detail_summary, entity_detail_description,
entity_detail_attrs, entity_detail_links, entity_detail_backlinks, entity_detail_no_backlinks,
entity_detail_not_created, entity_edit_title_new, entity_edit_title_edit, entity_edit_type,
entity_edit_name, entity_edit_summary, entity_edit_description, entity_edit_description_hint,
entity_edit_tags, entity_edit_delete_confirm, entity_conflict, entity_dirty, entity_not_found,
entity_created_hint,
attr_player_name, attr_race, attr_klass, attr_level, attr_hp, attr_ac, attr_status, attr_faction,
attr_location, attr_region, attr_kind, attr_parent, attr_owner, attr_quantity, attr_rarity,
attr_unknown, attr_not_set, attr_choose,
status_alive, status_dead, status_retired, status_unknown,
kind_city, kind_dungeon, kind_wild, kind_building, kind_weapon, kind_armor, kind_potion,
kind_treasure, kind_quest,
retelling_list_empty, retelling_list_drafts, retelling_list_new, retelling_detail_title,
retelling_field_title, retelling_field_date, retelling_field_author, retelling_field_body,
retelling_field_body_hint, retelling_author_none, retelling_edited, retelling_author,
retelling_status_draft, retelling_published, retelling_delete_confirm, retelling_edit_title_new,
retelling_edit_title_edit, retelling_publish_hint, retelling_links, retelling_conflict,
members_title, members_empty, members_my_role, members_add, members_role, members_linked_hero,
members_status, members_status_active, members_status_invited, members_status_removed,
members_remove_confirm, members_last_owner_error, member_email, member_email_hint, members_added,
members_invited, members_manage_forbidden,
invite_title, invite_empty, invite_create, invite_role, invite_linked, invite_token,
invite_expires, invite_uses, invite_link, invite_revoke_confirm, invite_qr_title, invite_created,
invite_camera_rationale, invite_camera_denied, invite_scan_title, invite_scan_hint,
invite_accept_title, invite_accept_campaign, invite_accept_role, invite_accept_linked,
invite_accept_expires, invite_accept_invalid, invite_accepted, invite_login_required,
search_title, search_hint, search_all, search_empty, search_prompt, search_results,
search_local_hint, search_kind_entity, search_kind_retelling,
settings_title, settings_sync_section, settings_wifi_only, settings_sync_now,
settings_full_resync, settings_full_resync_hint, settings_server, settings_user,
settings_account_section, settings_logout_confirm, settings_about, settings_resync_done,
loading, permission_denied, wiki_broken_link, wiki_create_suggestion,
cd_add, cd_search, cd_settings, cd_logout, cd_refresh, cd_delete, cd_edit, cd_more, cd_qr,
cd_scan, cd_sync, cd_icon, cd_close, cd_revoke

## Drawable (векторные иконки; другие использовать нельзя)

ic_add, ic_search, ic_settings, ic_logout, ic_refresh, ic_qr, ic_scan, ic_delete, ic_edit,
ic_sync, ic_offline, ic_warning, ic_check, ic_close, ic_more, ic_hero, ic_npc, ic_location,
ic_item, ic_retelling, ic_members, ic_link

## Цвета

colorPrimary, colorPrimaryDark, colorOnPrimary, colorSecondary, colorOnSecondary, colorSurface,
colorOnSurface, colorBackground, colorOnBackground, colorError, colorOnError, colorOutline,
wiki_link, wiki_link_broken, code_background, status_alive, status_dead, status_unknown,
badge_dirty, badge_conflict, badge_published

---

# ГРУППА A — splash, auth, campaigns, campaign, entity

## A1. activity_splash.xml
Корень: `FrameLayout` (match_parent), `android:background="?attr/colorBackground"`.
- `ImageView` id `splash_logo`, `@drawable/ic_hero`, 96dp, по центру, `app:tint="?attr/colorPrimary"`.
- `TextView` id `splash_title`, `@string/app_name`, `textAppearance="?attr/textAppearanceHeadlineSmall"`, ниже логотипа по центру.
- `ProgressBar` id `splash_progress`, `style="?android:attr/progressBarStyle"`, ниже, по центру.

## A2. activity_auth.xml
Корень: `androidx.coordinatorlayout.widget.CoordinatorLayout`.
Внутри — `androidx.core.widget.NestedScrollView` (`match_parent`) → `LinearLayout` vertical, padding 24dp:
- `TextView` id `auth_title`, `@string/auth_title`, `textAppearance="?attr/textAppearanceHeadlineSmall"`.
- `TextView` id `auth_invite_hint`, `@string/auth_invite_pending`, `visibility="gone"`, `textColor="?attr/colorPrimary"`.
- `com.google.android.material.tabs.TabLayout` id `auth_tabs`, `app:tabMode="fixed"` (вкладки задаются в Java).
- `LinearLayout` id `auth_login_group` vertical:
  - `TextInputLayout` (без id) → `TextInputEditText` id `auth_login_email`, `inputType="textEmailAddress"`, `hint="@string/auth_email"`.
  - `TextInputLayout` (без id, `app:passwordToggleEnabled="true"`) → `TextInputEditText` id `auth_login_password`, `inputType="textPassword"`, `hint="@string/auth_password"`.
  - `MaterialButton` id `auth_login_submit`, `@string/auth_login`, обычная кнопка, без атрибута style.
- `LinearLayout` id `auth_register_group` vertical, `visibility="gone"`:
  - `TextInputLayout` → `TextInputEditText` id `auth_register_name`, `hint="@string/auth_display_name"`.
  - `TextInputLayout` → `TextInputEditText` id `auth_register_email`, `inputType="textEmailAddress"`, `hint="@string/auth_email"`.
  - `TextInputLayout` («`app:passwordToggleEnabled="true"`») → `TextInputEditText` id `auth_register_password`, `inputType="textPassword"`, `hint="@string/auth_password"`.
  - `MaterialButton` id `auth_register_submit`, `@string/auth_register`.
- `TextView` id `auth_or_oauth`, `@string/auth_or_oauth`, gravity center.
- `LinearLayout` id `auth_oauth_group` horizontal:
  - `MaterialButton` id `auth_oauth_yandex`, `@string/auth_oauth_yandex`, `layout_weight="1"`.
  - `MaterialButton` id `auth_oauth_vk`, `@string/auth_oauth_vk`, `layout_weight="1"`.
- `ProgressBar` id `auth_progress`, `visibility="gone"`.
- `TextView` id `auth_error`, `textColor="?attr/colorError"`, `visibility="gone"`.

## A3. activity_campaign_list.xml
Корень: `androidx.coordinatorlayout.widget.CoordinatorLayout`.
- `com.google.android.material.appbar.AppBarLayout` → `com.google.android.material.appbar.MaterialToolbar`
  id `campaign_list_toolbar`, `app:title="@string/campaign_list_title"`.
- `LinearLayout` vertical с `app:layout_behavior="@string/appbar_scrolling_view_behavior"`:
  - `TextView` id `campaign_list_signed_as`, `@string/campaign_list_signed_as`, `textAppearance="?attr/textAppearanceBodySmall"`, padding 16dp/8dp.
  - `TextView` id `campaign_list_sync_status`, `@string/sync_status_idle`, `textAppearance="?attr/textAppearanceBodySmall"`.
  - `androidx.swiperefreshlayout.widget.SwipeRefreshLayout` id `campaign_list_swipe`, `layout_height="0dp"`, `layout_weight="1"`:
    - `androidx.recyclerview.widget.RecyclerView` id `campaign_list_recycler`, `match_parent`.
  - `LinearLayout` id `campaign_list_empty` vertical, `gravity="center"`, `visibility="gone"`, `layout_height="0dp"`, `layout_weight="1"`:
    - `TextView` (без id) `@string/campaign_list_empty` `textAppearance="?attr/textAppearanceTitleMedium"`.
    - `TextView` (без id) `@string/campaign_list_empty_hint`.
  - `ProgressBar` id `campaign_list_progress`, `visibility="gone"`.
- `com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton` id `campaign_list_fab`,
  `android:layout_gravity="bottom|end"`, `android:layout_margin="16dp"`, `android:text="@string/campaign_list_new"`,
  `app:icon="@drawable/ic_add"`.

## A4. item_campaign.xml
Корень: `com.google.android.material.card.MaterialCardView` id `item_campaign_root`,
`android:layout_width="match_parent"`, `android:layout_height="wrap_content"`,
`android:layout_marginStart="12dp"`, `android:layout_marginEnd="12dp"`, `android:layout_marginTop="6dp"`,
`android:layout_marginBottom="6dp"`, `app:cardCornerRadius="12dp"`, `android:clickable="true"`, `android:focusable="true"`.
Внутри `LinearLayout` vertical padding 16dp:
- `TextView` id `item_campaign_title`, `textAppearance="?attr/textAppearanceTitleMedium"`.
- `TextView` id `item_campaign_description`, `maxLines="2"`, `ellipsize="end"`, `textAppearance="?attr/textAppearanceBodyMedium"`.
- `TextView` id `item_campaign_role`, `textAppearance="?attr/textAppearanceLabelMedium"`, `textColor="?attr/colorPrimary"`.
- `TextView` id `item_campaign_counts`, `textAppearance="?attr/textAppearanceBodySmall"`.
- `TextView` id `item_campaign_updated`, `textAppearance="?attr/textAppearanceBodySmall"`.

## A5. dialog_campaign_edit.xml
Корень: `LinearLayout` vertical, padding 24dp.
- `TextView` id `campaign_edit_header`, `@string/campaign_edit_title_new`, `textAppearance="?attr/textAppearanceTitleMedium"`.
- `TextInputLayout` → `TextInputEditText` id `campaign_edit_title_input`, `hint="@string/campaign_field_title"`.
- `TextInputLayout` → `TextInputEditText` id `campaign_edit_description_input`,
  `hint="@string/campaign_field_description"`, `inputType="textMultiLine"`, `minLines="3"`, `gravity="top"`.

## A6. activity_campaign.xml
Корень: `LinearLayout` vertical.
- `com.google.android.material.appbar.MaterialToolbar` id `campaign_toolbar`.
- `com.google.android.material.tabs.TabLayout` id `campaign_tabs`, `app:tabMode="scrollable"`.
- `TextView` id `campaign_sync_status`, `@string/sync_status_idle`, `textAppearance="?attr/textAppearanceBodySmall"`, padding 16dp/4dp.
- `ProgressBar` id `campaign_progress`, `style="?android:attr/progressBarStyleHorizontal"`, `android:indeterminate="true"`, `visibility="gone"`.
- `androidx.viewpager2.widget.ViewPager2` id `campaign_pager`, `layout_height="0dp"`, `layout_weight="1"`.

## A7. fragment_entity_list.xml
Корень: `androidx.coordinatorlayout.widget.CoordinatorLayout`.
- `androidx.swiperefreshlayout.widget.SwipeRefreshLayout` id `entity_list_swipe` (`match_parent`):
  - `androidx.recyclerview.widget.RecyclerView` id `entity_list_recycler`, `match_parent`, padding 6dp, `clipToPadding="false"`.
- `TextView` id `entity_list_empty`, `@string/entity_list_empty`, `layout_gravity="center"`, `visibility="gone"`.
- `ProgressBar` id `entity_list_progress`, `layout_gravity="center"`, `visibility="gone"`.
- `com.google.android.material.floatingactionbutton.FloatingActionButton` id `entity_list_fab`,
  `layout_gravity="bottom|end"`, `layout_margin="16dp"`, `app:srcCompat="@drawable/ic_add"`,
  `android:contentDescription="@string/cd_add"`.

## A8. item_entity.xml
Корень: `com.google.android.material.card.MaterialCardView` id `item_entity_root`, как item_campaign (отступы 12/12/6/6, radius 12dp, clickable/focusable).
Внутри `LinearLayout` horizontal padding 12dp:
- `ImageView` id `item_entity_icon`, 32dp, `app:tint="?attr/colorPrimary"`, `android:contentDescription="@string/cd_icon"`.
- `LinearLayout` vertical `layout_weight="1"`, `layout_marginStart="12dp"`:
  - `TextView` id `item_entity_name`, `textAppearance="?attr/textAppearanceTitleMedium"`.
  - `TextView` id `item_entity_summary`, `maxLines="2"`, `ellipsize="end"`, `textAppearance="?attr/textAppearanceBodyMedium"`.
  - `TextView` id `item_entity_tags`, `textAppearance="?attr/textAppearanceBodySmall"`, `textColor="?attr/colorPrimary"`.
  - `TextView` id `item_entity_status`, `textAppearance="?attr/textAppearanceLabelSmall"`, `visibility="gone"`, `textColor="@color/badge_dirty"`.

## A9. activity_entity_detail.xml
Корень: `androidx.coordinatorlayout.widget.CoordinatorLayout`.
- `com.google.android.material.appbar.AppBarLayout` → `MaterialToolbar` id `entity_toolbar`, `app:title="@string/entity_detail_title"`.
- `androidx.core.widget.NestedScrollView` с `app:layout_behavior="@string/appbar_scrolling_view_behavior"` → `LinearLayout` vertical padding 16dp:
  - `TextView` id `entity_title`, `textAppearance="?attr/textAppearanceHeadlineSmall"`.
  - `TextView` id `entity_subtitle`, `textAppearance="?attr/textAppearanceBodySmall"`.
  - `TextView` id `entity_updated`, `textAppearance="?attr/textAppearanceBodySmall"`.
  - `LinearLayout` id `entity_conflict_bar` vertical `visibility="gone"` `android:background="@color/badge_conflict"`
    padding 12dp `layout_marginTop="8dp"`:
    - `TextView` id `entity_conflict_text`, `@string/entity_conflict`, `textColor="?attr/colorOnError"`.
    - `LinearLayout` horizontal:
      - `MaterialButton` id `entity_conflict_accept`, `@string/action_take_server`, `layout_weight="1"`.
      - `MaterialButton` id `entity_conflict_overwrite`, `@string/action_overwrite`, `layout_weight="1"`.
  - `TextView` id `entity_tags`, `textAppearance="?attr/textAppearanceLabelMedium"`, `layout_marginTop="8dp"`.
  - `TextView` id `entity_summary_title`, `@string/entity_detail_summary`, `textAppearance="?attr/textAppearanceTitleSmall"`, `layout_marginTop="12dp"`.
  - `TextView` id `entity_summary`, `textAppearance="?attr/textAppearanceBodyLarge"`.
  - `TextView` id `entity_description_title`, `@string/entity_detail_description`, `textAppearance="?attr/textAppearanceTitleSmall"`, `layout_marginTop="12dp"`.
  - `TextView` id `entity_description`, `textAppearance="?attr/textAppearanceBodyLarge"`.
  - `TextView` id `entity_attrs_title`, `@string/entity_detail_attrs`, `textAppearance="?attr/textAppearanceTitleSmall"`, `layout_marginTop="12dp"`.
  - `LinearLayout` id `entity_attrs`, vertical.
  - `TextView` id `entity_links_title`, `@string/entity_detail_links`, `textAppearance="?attr/textAppearanceTitleSmall"`, `layout_marginTop="12dp"`.
  - `LinearLayout` id `entity_links`, vertical.
  - `TextView` id `entity_backlinks_title`, `@string/entity_detail_backlinks`, `textAppearance="?attr/textAppearanceTitleSmall"`, `layout_marginTop="12dp"`.
  - `LinearLayout` id `entity_backlinks`, vertical.
  - `TextView` id `entity_backlinks_empty`, `@string/entity_detail_no_backlinks`, `textAppearance="?attr/textAppearanceBodySmall"`, `visibility="gone"`.
- `ProgressBar` id `entity_progress`, `layout_gravity="center"`, `visibility="gone"`.

## A10. activity_entity_edit.xml
Корень: `LinearLayout` vertical.
- `MaterialToolbar` id `entity_edit_toolbar`, `app:title="@string/entity_edit_title_edit"`.
- `androidx.core.widget.NestedScrollView` `layout_height="0dp"` `layout_weight="1"` → `LinearLayout` vertical padding 16dp:
  - `TextInputLayout` (`app:endIconMode="dropdown_menu"`) → `com.google.android.material.textfield.MaterialAutoCompleteTextView`
    id `entity_edit_type`, `hint="@string/entity_edit_type"`.
  - `TextInputLayout` → `TextInputEditText` id `entity_edit_name_input`, `hint="@string/entity_edit_name"`.
  - `TextInputLayout` → `TextInputEditText` id `entity_edit_summary_input`, `hint="@string/entity_edit_summary"`.
  - `TextInputLayout` (`app:helperText="@string/entity_edit_description_hint"`) →
    `TextInputEditText` id `entity_edit_description_input`, `hint="@string/entity_edit_description"`,
    `inputType="textMultiLine"`, `minLines="6"`, `gravity="top"`.
  - `TextInputLayout` → `TextInputEditText` id `entity_edit_tags_input`, `hint="@string/entity_edit_tags"`.
  - `TextView` (без id) `@string/entity_detail_attrs`, `textAppearance="?attr/textAppearanceTitleSmall"`.
  - `LinearLayout` id `entity_edit_attrs`, vertical.
- `LinearLayout` horizontal padding 16dp:
  - `MaterialButton` id `entity_edit_save`, `@string/action_save`, `layout_weight="1"`.
  - `MaterialButton` id `entity_edit_delete`, `@string/action_delete`, `layout_weight="1"`.
- `ProgressBar` id `entity_edit_progress`, `visibility="gone"`.

---

# ГРУППА B — retelling, members, invite, search, settings (+ все menu)

## B1. fragment_retelling_list.xml
Корень: `androidx.coordinatorlayout.widget.CoordinatorLayout`.
- `androidx.swiperefreshlayout.widget.SwipeRefreshLayout` id `retelling_list_swipe` (`match_parent`):
  - `androidx.core.widget.NestedScrollView` `match_parent` → `LinearLayout` vertical padding 6dp:
    - `TextView` id `retelling_list_drafts_title`, `@string/retelling_list_drafts`,
      `textAppearance="?attr/textAppearanceTitleSmall"`, padding 12dp, `visibility="gone"`.
    - `androidx.recyclerview.widget.RecyclerView` id `retelling_list_drafts`,
      `layout_height="wrap_content"`, `visibility="gone"`, `nestedScrollingEnabled="false"`.
    - `androidx.recyclerview.widget.RecyclerView` id `retelling_list_recycler`,
      `layout_height="wrap_content"`, `nestedScrollingEnabled="false"`.
- `TextView` id `retelling_list_empty`, `@string/retelling_list_empty`, `layout_gravity="center"`, `visibility="gone"`.
- `ProgressBar` id `retelling_list_progress`, `layout_gravity="center"`, `visibility="gone"`.
- `FloatingActionButton` id `retelling_list_fab`, `layout_gravity="bottom|end"`, `layout_margin="16dp"`,
  `app:srcCompat="@drawable/ic_add"`, `android:contentDescription="@string/cd_add"`.

## B2. item_retelling.xml
Корень: `MaterialCardView` id `item_retelling_root` (отступы 12/12/6/6, radius 12dp, clickable/focusable).
Внутри `LinearLayout` vertical padding 16dp:
- `TextView` id `item_retelling_title`, `textAppearance="?attr/textAppearanceTitleMedium"`.
- `TextView` id `item_retelling_meta`, `textAppearance="?attr/textAppearanceBodySmall"`.
- `TextView` id `item_retelling_snippet`, `maxLines="3"`, `ellipsize="end"`, `textAppearance="?attr/textAppearanceBodyMedium"`.
- `TextView` id `item_retelling_status`, `textAppearance="?attr/textAppearanceLabelSmall"`, `textColor="@color/badge_published"`.

## B3. item_retelling_draft.xml
Корень: `MaterialCardView` id `item_draft_root` (те же отступы, radius 12dp, clickable/focusable).
Внутри `LinearLayout` horizontal padding 16dp `gravity="center_vertical"`:
- `LinearLayout` vertical `layout_weight="1"`:
  - `TextView` id `item_draft_title`, `textAppearance="?attr/textAppearanceTitleSmall"`.
  - `TextView` id `item_draft_updated`, `textAppearance="?attr/textAppearanceBodySmall"`.
  - `TextView` id `item_draft_badge`, `@string/retelling_status_draft`, `textAppearance="?attr/textAppearanceLabelSmall"`, `textColor="@color/badge_dirty"`.
- `ImageButton` id `item_draft_delete`, `android:src="@drawable/ic_delete"`, `android:background="?attr/selectableItemBackgroundBorderless"`,
  `android:contentDescription="@string/cd_delete"`.

## B4. activity_retelling_detail.xml
Корень: `androidx.coordinatorlayout.widget.CoordinatorLayout`.
- `AppBarLayout` → `MaterialToolbar` id `retelling_toolbar`, `app:title="@string/retelling_detail_title"`.
- `NestedScrollView` с `app:layout_behavior="@string/appbar_scrolling_view_behavior"` → `LinearLayout` vertical padding 16dp:
  - `TextView` id `retelling_title`, `textAppearance="?attr/textAppearanceHeadlineSmall"`.
  - `TextView` id `retelling_meta`, `textAppearance="?attr/textAppearanceBodySmall"`.
  - `LinearLayout` id `retelling_conflict_bar` vertical `visibility="gone"`
    `android:background="@color/badge_conflict"` padding 12dp `layout_marginTop="8dp"`:
    - `TextView` id `retelling_conflict_text`, `@string/retelling_conflict`, `textColor="?attr/colorOnError"`.
    - `LinearLayout` horizontal:
      - `MaterialButton` id `retelling_conflict_accept`, `@string/action_take_server`, `layout_weight="1"`.
      - `MaterialButton` id `retelling_conflict_overwrite`, `@string/action_overwrite`, `layout_weight="1"`.
  - `TextView` id `retelling_body`, `textAppearance="?attr/textAppearanceBodyLarge"`, `layout_marginTop="12dp"`.
  - `TextView` id `retelling_links_title`, `@string/retelling_links`, `textAppearance="?attr/textAppearanceTitleSmall"`, `layout_marginTop="16dp"`.
  - `LinearLayout` id `retelling_links`, vertical.
- `ProgressBar` id `retelling_progress`, `layout_gravity="center"`, `visibility="gone"`.

## B5. activity_retelling_edit.xml
Корень: `LinearLayout` vertical.
- `MaterialToolbar` id `retelling_edit_toolbar`, `app:title="@string/retelling_edit_title_new"`.
- `NestedScrollView` `layout_height="0dp"` `layout_weight="1"` → `LinearLayout` vertical padding 16dp:
  - `TextInputLayout` → `TextInputEditText` id `retelling_edit_title_input`, `hint="@string/retelling_field_title"`.
  - `TextInputLayout` → `TextInputEditText` id `retelling_edit_date_input`, `hint="@string/retelling_field_date"`, `inputType="date"`.
  - `TextInputLayout` (`app:endIconMode="dropdown_menu"`) → `MaterialAutoCompleteTextView`
    id `retelling_edit_author_input`, `hint="@string/retelling_field_author"`.
  - `TextInputLayout` (`app:helperText="@string/retelling_field_body_hint"`) →
    `TextInputEditText` id `retelling_edit_body_input`, `hint="@string/retelling_field_body"`,
    `inputType="textMultiLine"`, `minLines="8"`, `gravity="top"`.
  - `TextView` id `retelling_edit_preview`, `textAppearance="?attr/textAppearanceBodyLarge"`,
    `visibility="gone"`, `padding="8dp"`.
  - `TextView` id `retelling_edit_publish_hint`, `@string/retelling_publish_hint`,
    `textAppearance="?attr/textAppearanceBodySmall"`.
- `LinearLayout` horizontal padding 16dp:
  - `MaterialButton` id `retelling_edit_save_draft`, `@string/action_save_draft`, `layout_weight="1"`.
  - `MaterialButton` id `retelling_edit_publish`, `@string/action_publish`, `layout_weight="1"`.
- `LinearLayout` horizontal padding 16dp:
  - `MaterialButton` id `retelling_edit_preview_toggle`, `@string/action_preview`, `layout_weight="1"`.
- `ProgressBar` id `retelling_edit_progress`, `visibility="gone"`.

## B6. fragment_members.xml
Корень: `androidx.coordinatorlayout.widget.CoordinatorLayout`.
- `androidx.swiperefreshlayout.widget.SwipeRefreshLayout` id `members_swipe` (`match_parent`):
  - `LinearLayout` vertical:
    - `TextView` id `members_current`, `textAppearance="?attr/textAppearanceBodySmall"`, padding 16dp/8dp.
    - `androidx.recyclerview.widget.RecyclerView` id `members_recycler`, `layout_height="0dp"`
      (используйте `layout_height="match_parent"` внутри SwipeRefreshLayout без weight;
      ориентир: RecyclerView занимает всё место) — `match_parent`.
- `TextView` id `members_empty`, `@string/members_empty`, `layout_gravity="center"`, `visibility="gone"`.
- `ProgressBar` id `members_progress`, `layout_gravity="center"`, `visibility="gone"`.
- `ExtendedFloatingActionButton` id `members_add`, `layout_gravity="bottom|end"`, `layout_margin="16dp"`,
  `android:text="@string/members_add"`, `app:icon="@drawable/ic_add"`, `visibility="gone"`.

## B7. item_member.xml
Корень: `MaterialCardView` id `item_member_root` (отступы 12/12/6/6, radius 12dp, clickable/focusable).
Внутри `LinearLayout` horizontal padding 16dp `gravity="center_vertical"`:
- `LinearLayout` vertical `layout_weight="1"`:
  - `TextView` id `item_member_name`, `textAppearance="?attr/textAppearanceTitleMedium"`.
  - `TextView` id `item_member_email`, `textAppearance="?attr/textAppearanceBodySmall"`.
  - `TextView` id `item_member_role`, `textAppearance="?attr/textAppearanceLabelMedium"`, `textColor="?attr/colorPrimary"`.
  - `TextView` id `item_member_linked`, `textAppearance="?attr/textAppearanceBodySmall"`.
  - `TextView` id `item_member_status`, `textAppearance="?attr/textAppearanceLabelSmall"`.
- `ImageButton` id `item_member_more`, `android:src="@drawable/ic_more"`,
  `android:background="?attr/selectableItemBackgroundBorderless"`, `android:contentDescription="@string/cd_more"`.

## B8. dialog_member_edit.xml
Корень: `LinearLayout` vertical padding 24dp.
- `TextView` id `member_edit_header`, `@string/members_add`, `textAppearance="?attr/textAppearanceTitleMedium"`.
- `TextInputLayout` → `TextInputEditText` id `member_edit_email_input`,
  `hint="@string/member_email"`, `inputType="textEmailAddress"`.
- `TextInputLayout` (`app:endIconMode="dropdown_menu"`) → `MaterialAutoCompleteTextView`
  id `member_edit_role_input`, `hint="@string/members_role"`.
- `TextInputLayout` (`app:endIconMode="dropdown_menu"`) → `MaterialAutoCompleteTextView`
  id `member_edit_linked_input`, `hint="@string/members_linked_hero"`.

## B9. activity_invite.xml
Корень: `androidx.coordinatorlayout.widget.CoordinatorLayout`.
- `AppBarLayout` → `MaterialToolbar` id `invite_toolbar`, `app:title="@string/invite_title"`.
- `LinearLayout` id `invite_qr_group` vertical `visibility="gone"` padding 16dp
  `android:background="?attr/colorSurface"`:
  - `TextView` (без id) `@string/invite_qr_title`, `textAppearance="?attr/textAppearanceTitleMedium"`.
  - `ImageView` id `invite_qr_image`, 240dp, `layout_gravity="center_horizontal"`,
    `android:contentDescription="@string/cd_qr"`.
  - `TextView` id `invite_qr_token`, `textAppearance="?attr/textAppearanceTitleLarge"`.
  - `TextView` id `invite_qr_link`, `textAppearance="?attr/textAppearanceBodySmall"`.
  - `MaterialButton` id `invite_qr_close`, `@string/action_close`.
- `androidx.swiperefreshlayout.widget.SwipeRefreshLayout` id `invite_swipe`
  с `app:layout_behavior="@string/appbar_scrolling_view_behavior"`:
  - `androidx.recyclerview.widget.RecyclerView` id `invite_recycler`, `match_parent`.
- `TextView` id `invite_empty`, `@string/invite_empty`, `layout_gravity="center"`, `visibility="gone"`.
- `ProgressBar` id `invite_progress`, `layout_gravity="center"`, `visibility="gone"`.
- `ExtendedFloatingActionButton` id `invite_create`, `layout_gravity="bottom|end"`, `layout_margin="16dp"`,
  `android:text="@string/invite_create"`, `app:icon="@drawable/ic_add"`.
- `ExtendedFloatingActionButton` id `invite_scan`, `layout_gravity="bottom|start"`, `layout_margin="16dp"`,
  `android:text="@string/action_scan_qr"`, `app:icon="@drawable/ic_scan"`.

## B10. item_invite.xml
Корень: `MaterialCardView` id `item_invite_root` (отступы 12/12/6/6, radius 12dp).
Внутри `LinearLayout` horizontal padding 16dp `gravity="center_vertical"`:
- `LinearLayout` vertical `layout_weight="1"`:
  - `TextView` id `item_invite_token`, `textAppearance="?attr/textAppearanceTitleMedium"`.
  - `TextView` id `item_invite_role`, `textAppearance="?attr/textAppearanceBodyMedium"`.
  - `TextView` id `item_invite_expires`, `textAppearance="?attr/textAppearanceBodySmall"`.
  - `TextView` id `item_invite_uses`, `textAppearance="?attr/textAppearanceBodySmall"`.
- `ImageButton` id `item_invite_revoke`, `android:src="@drawable/ic_delete"`,
  `android:background="?attr/selectableItemBackgroundBorderless"`, `android:contentDescription="@string/cd_revoke"`.

## B11. activity_scan.xml
Корень: `FrameLayout` (match_parent).
- `com.journeyapps.barcodescanner.DecoratedBarcodeView` id `scan_preview`, `match_parent`.
- `TextView` id `scan_status`, `@string/invite_scan_hint`, `layout_gravity="bottom|center_horizontal"`,
  `android:layout_marginBottom="48dp"`, `textColor="@android:color/white"`,
  `android:background="#80000000"`, padding 12dp.

## B12. activity_invite_accept.xml
Корень: `LinearLayout` vertical padding 24dp `gravity="center"`.
- `ProgressBar` id `invite_accept_progress`, `visibility="gone"`.
- `TextView` id `invite_accept_title`, `@string/invite_accept_title`, `textAppearance="?attr/textAppearanceHeadlineSmall"`.
- `TextView` id `invite_accept_campaign`, `textAppearance="?attr/textAppearanceTitleMedium"`, `layout_marginTop="12dp"`.
- `TextView` id `invite_accept_role`, `textAppearance="?attr/textAppearanceBodyMedium"`.
- `TextView` id `invite_accept_linked`, `textAppearance="?attr/textAppearanceBodyMedium"`.
- `TextView` id `invite_accept_expires`, `textAppearance="?attr/textAppearanceBodySmall"`.
- `TextView` id `invite_accept_error`, `textColor="?attr/colorError"`, `visibility="gone"`, `layout_marginTop="12dp"`.
- `MaterialButton` id `invite_accept_button`, `@string/action_accept`, `layout_marginTop="16dp"`,
  `layout_width="match_parent"`.

## B13. fragment_search.xml
Корень: `LinearLayout` vertical.
- `TextInputLayout` (`app:endIconMode="clear_text"`) → `TextInputEditText` id `search_input`,
  `hint="@string/search_hint"`, `inputType="text"`, `imeOptions="actionSearch"`.
- `TextView` id `search_hint_text`, `@string/search_local_hint`, `textAppearance="?attr/textAppearanceBodySmall"`, padding 16dp/4dp.
- `com.google.android.material.chip.ChipGroup` id `search_chips`, `app:singleSelection="true"`,
  `app:selectionRequired="false"` (чипы создаются в Java).
- `FrameLayout` `layout_height="0dp"` `layout_weight="1"`:
  - `androidx.recyclerview.widget.RecyclerView` id `search_recycler`, `match_parent`, padding 6dp, `clipToPadding="false"`.
  - `TextView` id `search_empty`, `@string/search_empty`, `layout_gravity="center"`, `visibility="gone"`.
  - `ProgressBar` id `search_progress`, `layout_gravity="center"`, `visibility="gone"`.

## B14. item_search.xml
Корень: `MaterialCardView` id `item_search_root` (отступы 12/12/6/6, radius 12dp, clickable/focusable).
Внутри `LinearLayout` vertical padding 12dp:
- `TextView` id `item_search_title`, `textAppearance="?attr/textAppearanceTitleSmall"`.
- `TextView` id `item_search_snippet`, `maxLines="2"`, `ellipsize="end"`, `textAppearance="?attr/textAppearanceBodySmall"`.
- `TextView` id `item_search_kind`, `textAppearance="?attr/textAppearanceLabelSmall"`, `textColor="?attr/colorPrimary"`.

## B15. activity_search.xml
Корень: `LinearLayout` vertical.
- `MaterialToolbar` id `search_toolbar`, `app:title="@string/search_title"`.
- `FrameLayout` id `search_container`, `layout_height="0dp"`, `layout_weight="1"`.

## B16. activity_settings.xml
Корень: `LinearLayout` vertical.
- `MaterialToolbar` id `settings_toolbar`, `app:title="@string/settings_title"`.
- `androidx.core.widget.NestedScrollView` `layout_height="0dp"` `layout_weight="1"` → `LinearLayout` vertical padding 16dp:
  - `TextView` (без id) `@string/settings_account_section`, `textAppearance="?attr/textAppearanceTitleSmall"`.
  - `TextView` id `settings_user`, `textAppearance="?attr/textAppearanceBodyMedium"`.
  - `TextView` id `settings_server`, `textAppearance="?attr/textAppearanceBodySmall"`.
  - `TextView` (без id) `@string/settings_sync_section`, `textAppearance="?attr/textAppearanceTitleSmall"`, `layout_marginTop="16dp"`.
  - `com.google.android.material.materialswitch.MaterialSwitch` id `settings_wifi_only`,
    `android:text="@string/settings_wifi_only"` (если MaterialSwitch недоступен в вашей версии —
    используйте `com.google.android.material.switchmaterial.SwitchMaterial`).
  - `TextView` id `settings_last_sync`, `textAppearance="?attr/textAppearanceBodySmall"`.
  - `MaterialButton` id `settings_sync_now`, `@string/settings_sync_now`.
  - `MaterialButton` id `settings_full_resync`, `@string/settings_full_resync`.
  - `TextView` (без id) `@string/settings_full_resync_hint`, `textAppearance="?attr/textAppearanceBodySmall"`.
  - `MaterialButton` id `settings_logout`, `@string/action_logout`, `layout_marginTop="16dp"`.
  - `TextView` id `settings_about`, `textAppearance="?attr/textAppearanceBodySmall"`, `layout_marginTop="16dp"`.
  - `ProgressBar` id `settings_progress`, `visibility="gone"`.

---

# МЕНЮ (каталог `res/menu/`)

Идентификаторы пунктов меню (`android:id`) — это тоже `R.id`, их набор строго фиксирован.

## menu_campaign_list.xml
`<menu xmlns:android=... xmlns:app=...>`:
- item id `action_search`, `android:title="@string/action_search"`, `android:icon="@drawable/ic_search"`, `app:showAsAction="ifRoom"`.
- item id `action_settings`, `android:title="@string/action_settings"`, `android:icon="@drawable/ic_settings"`, `app:showAsAction="ifRoom"`.
- item id `action_logout`, `android:title="@string/action_logout"`, `android:icon="@drawable/ic_logout"`, `app:showAsAction="never"`.

## menu_campaign.xml
- item id `action_search`, `@string/action_search`, `@drawable/ic_search`, `app:showAsAction="ifRoom"`.
- item id `action_invite`, `@string/action_invite`, `@drawable/ic_qr`, `app:showAsAction="ifRoom"`.
- item id `action_refresh`, `@string/action_refresh`, `@drawable/ic_refresh`, `app:showAsAction="never"`.

## menu_entity_list.xml
- item id `action_search`, `@string/action_search`, `@drawable/ic_search`, `app:showAsAction="ifRoom"`.
- item id `action_refresh`, `@string/action_refresh`, `@drawable/ic_refresh`, `app:showAsAction="never"`.

## menu_entity_detail.xml
- item id `action_edit`, `@string/action_edit`, `@drawable/ic_edit`, `app:showAsAction="ifRoom"`.
- item id `action_delete`, `@string/action_delete`, `@drawable/ic_delete`, `app:showAsAction="never"`.

## menu_retelling_list.xml
- item id `action_search`, `@string/action_search`, `@drawable/ic_search`, `app:showAsAction="ifRoom"`.

## menu_retelling_detail.xml
- item id `action_edit`, `@string/action_edit`, `@drawable/ic_edit`, `app:showAsAction="ifRoom"`.
- item id `action_delete`, `@string/action_delete`, `@drawable/ic_delete`, `app:showAsAction="never"`.
