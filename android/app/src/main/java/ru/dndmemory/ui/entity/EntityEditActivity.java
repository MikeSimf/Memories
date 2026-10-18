package ru.dndmemory.ui.entity;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.MenuItem;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import java.util.ArrayList;
import java.util.List;

import ru.dndmemory.App;
import ru.dndmemory.AppContainer;
import ru.dndmemory.R;
import ru.dndmemory.data.local.entity.CampaignRoom;
import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.data.local.entity.MemberRoom;
import ru.dndmemory.data.model.EntityType;
import ru.dndmemory.data.model.HeroItem;
import ru.dndmemory.databinding.ActivityEntityEditBinding;
import ru.dndmemory.databinding.ItemHeroItemEditBinding;
import ru.dndmemory.ui.campaign.CampaignViewModel;
import ru.dndmemory.ui.common.ErrorMessages;
import ru.dndmemory.ui.common.Extras;
import ru.dndmemory.ui.common.Labels;
import ru.dndmemory.ui.common.NameIndex;
import ru.dndmemory.ui.common.Rights;
import ru.dndmemory.ui.common.ViewModelFactory;
import ru.dndmemory.ui.common.ViewUtils;
import ru.dndmemory.util.AppExecutors;

/**
 * Редактор объекта. Набор полей зависит от класса (docs/api.md, раздел 6):
 * у героя — раса, класс, игрок и котомка; у персонажа — раса и краткое описание;
 * у локации своих полей нет; у предмета — владелец и необязательная ссылка.
 *
 * <p>Героя заводит и правит только владелец кампании: остальным редактор
 * не открывается (docs/roles.md).
 */
public class EntityEditActivity extends AppCompatActivity {

    private ActivityEntityEditBinding binding;
    private EntityEditViewModel viewModel;
    private CampaignViewModel campaignViewModel;

    private String campaignId;
    private boolean creating;
    private boolean rightsBlocked;

    private NameIndex nameIndex = NameIndex.of(null);
    private List<EntityRoom> candidates = new ArrayList<>();
    private List<MemberRoom> members = new ArrayList<>();

    private EntityType currentKind = EntityType.CHARACTER;
    private String selectedPlayerUserId;
    private String selectedOwnerId;

    private final List<ItemHeroItemEditBinding> itemRows = new ArrayList<>();

    private static final EntityType[] TYPE_VALUES = {
            EntityType.HERO, EntityType.CHARACTER, EntityType.LOCATION, EntityType.ITEM};

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEntityEditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        campaignId = getIntent() == null ? null : getIntent().getStringExtra(Extras.CAMPAIGN_ID);
        String entityId = getIntent() == null ? null : getIntent().getStringExtra(Extras.ENTITY_ID);
        String kindExtra = getIntent() == null ? null : getIntent().getStringExtra(Extras.ENTITY_TYPE);
        creating = getIntent() != null && getIntent().getBooleanExtra(Extras.CREATE_NEW, false);
        if (campaignId == null) {
            finish();
            return;
        }
        EntityType requested = EntityType.from(kindExtra);
        if (requested != null) {
            currentKind = requested;
        }

        AppContainer container = App.container(this);
        viewModel = new ViewModelProvider(this, ViewModelFactory.of(
                () -> new EntityEditViewModel(container.entityRepository(),
                        container.memberRepository(), AppExecutors.get())))
                .get(EntityEditViewModel.class);
        viewModel.start(campaignId, entityId, currentKind, creating);

        campaignViewModel = new ViewModelProvider(this, ViewModelFactory.of(
                () -> new CampaignViewModel(container.campaignRepository(),
                        container.authRepository(), container.syncEngine(),
                        container.syncScheduler())))
                .get(CampaignViewModel.class);
        campaignViewModel.setCampaignId(campaignId);
        campaignViewModel.refreshDetails();

        setSupportActionBar(binding.entityEditToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(creating
                    ? R.string.entity_edit_title_new : R.string.entity_edit_title_edit);
        }

        binding.entityEditType.setSimpleItems(typeLabels());
        binding.entityEditType.setOnItemClickListener((parent, view, position, id) -> {
            EntityType selected = selectedType();
            if (selected != currentKind) {
                collect();
                currentKind = selected;
                bindFieldsForKind();
            }
        });
        // Класс существующего объекта не меняется: у каждого класса свой эндпоинт.
        binding.entityEditType.setEnabled(creating);
        binding.entityEditTypeGroup.setEnabled(creating);

        binding.entityEditSave.setOnClickListener(v -> {
            collect();
            viewModel.save();
        });
        binding.entityEditDelete.setOnClickListener(v -> confirmDelete());
        ViewUtils.show(binding.entityEditDelete, !creating);

        binding.entityEditAddItem.setOnClickListener(v -> addItemRow(null));
        binding.entityEditPlayer.setOnItemClickListener((parent, view, position, id) ->
                selectedPlayerUserId = playerIdAt(position));
        binding.entityEditOwnerLink.setOnItemClickListener((parent, view, position, id) ->
                selectedOwnerId = ownerIdAt(position));

        observe();
    }

    private String[] typeLabels() {
        List<String> labels = new ArrayList<>();
        for (EntityType type : allowedTypes()) {
            labels.add(getString(Labels.entityType(type)));
        }
        return labels.toArray(new String[0]);
    }

    /** При создании список классов ограничен правами: герой — только владельцу. */
    @NonNull
    private List<EntityType> allowedTypes() {
        List<EntityType> out = new ArrayList<>();
        if (!creating) {
            out.add(currentKind);
            return out;
        }
        CampaignRoom campaign = campaignViewModel.campaign().getValue();
        for (EntityType type : TYPE_VALUES) {
            if (Rights.canWrite(campaign, type)) {
                out.add(type);
            }
        }
        if (!out.contains(currentKind)) {
            out.add(currentKind);
        }
        return out;
    }

    @NonNull
    private EntityType selectedType() {
        CharSequence shown = binding.entityEditType.getText();
        if (shown != null) {
            for (EntityType type : allowedTypes()) {
                if (getString(Labels.entityType(type)).contentEquals(shown)) {
                    return type;
                }
            }
        }
        return currentKind;
    }

    private void observe() {
        viewModel.state().observe(this, state -> {
            if (state == null) {
                return;
            }
            nameIndex = state.nameIndex;
            candidates = state.candidates;
            members = state.members;
            currentKind = EntityType.fromOrCharacter(state.entity.kind);

            binding.entityEditNameInput.setText(state.entity.name);
            binding.entityEditDescriptionInput.setText(state.entity.description);
            binding.entityEditType.setText(getString(Labels.entityType(currentKind)), false);
            selectedPlayerUserId = state.entity.playerUserId;
            selectedOwnerId = state.entity.ownerHeroId != null
                    ? state.entity.ownerHeroId : state.entity.ownerCharacterId;

            bindFieldsForKind();
            checkRights();
        });

        campaignViewModel.campaign().observe(this, campaign -> {
            if (creating) {
                binding.entityEditType.setSimpleItems(typeLabels());
            }
            checkRights();
        });

        viewModel.actionState().observe(this, resource -> {
            if (resource == null) {
                return;
            }
            ViewUtils.show(binding.entityEditProgress, resource.isLoading());
            binding.entityEditSave.setEnabled(!resource.isLoading());
            if (resource.isError()) {
                ErrorMessages.show(binding.getRoot(), resource.error);
                viewModel.clearActionState();
            }
        });

        viewModel.finishRequested().observe(this, finished -> {
            if (Boolean.TRUE.equals(finished)) {
                ViewUtils.toast(this, R.string.saved);
                finish();
            }
        });
    }

    /**
     * Героя ведёт только владелец: если прав нет, редактор закрывается, чтобы
     * объект нельзя было даже открыть на изменение (docs/roles.md).
     */
    private void checkRights() {
        CampaignRoom campaign = campaignViewModel.campaign().getValue();
        if (campaign == null || rightsBlocked) {
            return;
        }
        if (!Rights.canWrite(campaign, currentKind)) {
            rightsBlocked = true;
            android.util.Log.w("DndRights", "редактор закрыт: роль=" + campaign.myRole
                    + " права=" + campaign.myPermissions + " класс=" + currentKind);
            ViewUtils.toast(this, currentKind.requiresHeroPermission()
                    ? R.string.entity_hero_owner_only : R.string.error_forbidden);
            finish();
        }
    }

    /** Показывает поля текущего класса и заполняет их значениями объекта. */
    private void bindFieldsForKind() {
        EntityRoom draft = currentDraft();

        boolean hero = currentKind == EntityType.HERO;
        boolean character = currentKind == EntityType.CHARACTER;
        boolean item = currentKind == EntityType.ITEM;

        ViewUtils.show(binding.entityEditRaceGroup, hero || character);
        ViewUtils.show(binding.entityEditKlassGroup, hero);
        ViewUtils.show(binding.entityEditPlayerGroup, hero);
        ViewUtils.show(binding.entityEditShortGroup, character);
        ViewUtils.show(binding.entityEditOwnerNameGroup, item);
        ViewUtils.show(binding.entityEditOwnerLinkGroup, item);
        ViewUtils.show(binding.entityEditItemsGroup, hero);
        ViewUtils.show(binding.entityEditFieldsTitle, hero || character || item);

        if (draft != null) {
            binding.entityEditRaceInput.setText(draft.race);
            binding.entityEditKlassInput.setText(draft.klass);
            binding.entityEditShortInput.setText(draft.shortDescription);
            binding.entityEditOwnerNameInput.setText(draft.ownerName);
            binding.entityEditNameInput.setText(draft.name);
            binding.entityEditDescriptionInput.setText(draft.description);
        }

        bindPlayerDropdown();
        bindOwnerDropdown();
        bindItems();
    }

    @Nullable
    private EntityRoom currentDraft() {
        EntityEditViewModel.EditState state = viewModel.state().getValue();
        return state == null ? null : state.entity;
    }

    private void bindPlayerDropdown() {
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.entity_player_none));
        for (MemberRoom member : members) {
            if (member.status != null && "REMOVED".equalsIgnoreCase(member.status)) {
                continue;
            }
            labels.add(TextUtils.isEmpty(member.displayName)
                    ? getString(R.string.attr_not_set) : member.displayName);
        }
        binding.entityEditPlayer.setSimpleItems(labels.toArray(new String[0]));
        EntityRoom draft = currentDraft();
        String currentId = selectedPlayerUserId != null ? selectedPlayerUserId
                : (draft == null ? null : draft.playerUserId);
        String shown = labels.get(0);
        for (int i = 1; i < labels.size(); i++) {
            if (playerIdAt(i) != null && playerIdAt(i).equals(currentId)) {
                shown = labels.get(i);
                break;
            }
        }
        binding.entityEditPlayer.setText(shown, false);
        selectedPlayerUserId = playerIdAt(indexOf(labels, shown));
    }

    @Nullable
    private String playerIdAt(int position) {
        if (position <= 0) {
            return null;
        }
        List<MemberRoom> active = activeMembers();
        int index = position - 1;
        return index < active.size() ? active.get(index).userId : null;
    }

    @NonNull
    private List<MemberRoom> activeMembers() {
        List<MemberRoom> out = new ArrayList<>();
        for (MemberRoom member : members) {
            if (member.status == null || !"REMOVED".equalsIgnoreCase(member.status)) {
                out.add(member);
            }
        }
        return out;
    }

    private void bindOwnerDropdown() {
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.entity_owner_none));
        for (EntityRoom hero : ownerCandidates(EntityType.HERO)) {
            labels.add(getString(R.string.entity_owner_hero, displayName(hero)));
        }
        for (EntityRoom character : ownerCandidates(EntityType.CHARACTER)) {
            labels.add(getString(R.string.entity_owner_character, displayName(character)));
        }
        binding.entityEditOwnerLink.setSimpleItems(labels.toArray(new String[0]));
        EntityRoom draft = currentDraft();
        String currentId = selectedOwnerId != null ? selectedOwnerId
                : (draft == null ? null : (draft.ownerHeroId != null
                        ? draft.ownerHeroId : draft.ownerCharacterId));
        String shown = labels.get(0);
        for (int i = 1; i < labels.size(); i++) {
            if (currentId != null && currentId.equals(ownerIdAt(i))) {
                shown = labels.get(i);
                break;
            }
        }
        binding.entityEditOwnerLink.setText(shown, false);
        selectedOwnerId = ownerIdAt(indexOf(labels, shown));
    }

    @NonNull
    private List<EntityRoom> ownerCandidates(EntityType kind) {
        List<EntityRoom> out = new ArrayList<>();
        for (EntityRoom candidate : candidates) {
            if (kind.name().equalsIgnoreCase(candidate.kind)
                    && !TextUtils.isEmpty(candidate.name)) {
                out.add(candidate);
            }
        }
        return out;
    }

    @Nullable
    private String ownerIdAt(int position) {
        if (position <= 0) {
            return null;
        }
        List<EntityRoom> all = new ArrayList<>();
        all.addAll(ownerCandidates(EntityType.HERO));
        all.addAll(ownerCandidates(EntityType.CHARACTER));
        int index = position - 1;
        return index < all.size() ? all.get(index).id : null;
    }

    @Nullable
    private EntityRoom ownerById(@Nullable String id) {
        if (id == null) {
            return null;
        }
        for (EntityRoom candidate : candidates) {
            if (id.equals(candidate.id)) {
                return candidate;
            }
        }
        return null;
    }

    private static int indexOf(@NonNull List<String> labels, @NonNull String value) {
        for (int i = 0; i < labels.size(); i++) {
            if (labels.get(i).equals(value)) {
                return i;
            }
        }
        return 0;
    }

    private static String displayName(@NonNull EntityRoom room) {
        return TextUtils.isEmpty(room.name) ? "" : room.name;
    }

    // --- Котомка героя --------------------------------------------------------

    private void bindItems() {
        binding.entityEditItems.removeAllViews();
        itemRows.clear();
        EntityRoom draft = currentDraft();
        if (draft == null || draft.items == null) {
            return;
        }
        for (HeroItem item : draft.items) {
            addItemRow(item);
        }
    }

    private void addItemRow(@Nullable HeroItem item) {
        ItemHeroItemEditBinding row = ItemHeroItemEditBinding.inflate(
                getLayoutInflater(), binding.entityEditItems, false);
        if (item != null) {
            row.heroItemName.setText(item.name);
            if (item.quantity != null) {
                row.heroItemQuantity.setText(String.valueOf(item.quantity));
            }
            row.heroItemNotes.setText(item.notes);
        }
        row.heroItemRemove.setOnClickListener(v -> {
            binding.entityEditItems.removeView(row.getRoot());
            itemRows.remove(row);
        });
        binding.entityEditItems.addView(row.getRoot());
        itemRows.add(row);
    }

    @NonNull
    private List<HeroItem> collectItems() {
        List<HeroItem> out = new ArrayList<>();
        for (ItemHeroItemEditBinding row : itemRows) {
            HeroItem item = new HeroItem();
            item.name = text(row.heroItemName.getText());
            if (item.name.isEmpty()) {
                continue;
            }
            item.quantity = parseInt(row.heroItemQuantity.getText());
            String notes = text(row.heroItemNotes.getText());
            item.notes = notes.isEmpty() ? null : notes;
            out.add(item);
        }
        return out;
    }

    @Nullable
    private static Integer parseInt(@Nullable CharSequence value) {
        String raw = value == null ? "" : value.toString().trim();
        if (raw.isEmpty()) {
            return null;
        }
        try {
            return Integer.valueOf(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    // --- Сохранение -----------------------------------------------------------

    /** Переносит значения формы в ViewModel перед сохранением. */
    private void collect() {
        viewModel.setKind(selectedType());
        viewModel.setName(text(binding.entityEditNameInput.getText()));
        viewModel.setDescription(text(binding.entityEditDescriptionInput.getText()));

        if (currentKind == EntityType.HERO) {
            viewModel.setRace(text(binding.entityEditRaceInput.getText()));
            viewModel.setKlass(text(binding.entityEditKlassInput.getText()));
            MemberRoom player = memberById(selectedPlayerUserId);
            viewModel.setPlayerUserId(selectedPlayerUserId,
                    player == null ? null : player.displayName);
            viewModel.setItems(collectItems());
        } else if (currentKind == EntityType.CHARACTER) {
            viewModel.setRace(text(binding.entityEditRaceInput.getText()));
            viewModel.setShortDescription(text(binding.entityEditShortInput.getText()));
        } else if (currentKind == EntityType.ITEM) {
            viewModel.setOwnerName(text(binding.entityEditOwnerNameInput.getText()));
            viewModel.setOwnerLink(ownerById(selectedOwnerId));
        }
    }

    @Nullable
    private MemberRoom memberById(@Nullable String userId) {
        if (userId == null) {
            return null;
        }
        for (MemberRoom member : members) {
            if (userId.equals(member.userId)) {
                return member;
            }
        }
        return null;
    }

    private void confirmDelete() {
        String name = text(binding.entityEditNameInput.getText());
        String title = name.isEmpty() ? getString(R.string.entity_detail_title) : name;
        new MaterialAlertDialogBuilder(this)
                .setMessage(getString(R.string.entity_edit_delete_confirm, title))
                .setPositiveButton(R.string.action_delete, (dialog, which) -> viewModel.delete())
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    private static String text(@Nullable CharSequence value) {
        return value == null ? "" : value.toString().trim();
    }
}
