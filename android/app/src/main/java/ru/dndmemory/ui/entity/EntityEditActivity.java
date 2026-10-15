package ru.dndmemory.ui.entity;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.text.TextUtils;
import android.view.MenuItem;
import android.view.View;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import ru.dndmemory.App;
import ru.dndmemory.AppContainer;
import ru.dndmemory.R;
import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.data.model.EntityType;
import ru.dndmemory.databinding.ActivityEntityEditBinding;
import ru.dndmemory.ui.common.Attrs;
import ru.dndmemory.ui.common.ErrorMessages;
import ru.dndmemory.ui.common.Extras;
import ru.dndmemory.ui.common.Labels;
import ru.dndmemory.ui.common.NameIndex;
import ru.dndmemory.ui.common.ViewModelFactory;
import ru.dndmemory.ui.common.ViewUtils;
import ru.dndmemory.util.AppExecutors;

/**
 * Редактор объекта. Набор полей характеристик строится по типу
 * (см. {@link Attrs}): герой, НПС, локация и предмет имеют разные attrs.
 */
public class EntityEditActivity extends AppCompatActivity {

    /** Строка формы: либо текстовое поле, либо выпадающий список. */
    private static final class AttrRow {

        final Attrs.Field field;
        @Nullable
        final TextInputEditText text;
        @Nullable
        final MaterialAutoCompleteTextView dropdown;

        AttrRow(Attrs.Field field, @Nullable TextInputEditText text,
                @Nullable MaterialAutoCompleteTextView dropdown) {
            this.field = field;
            this.text = text;
            this.dropdown = dropdown;
        }

        @NonNull
        String value(@NonNull Context context, @NonNull NameIndex index) {
            if (text != null) {
                CharSequence raw = text.getText();
                return raw == null ? "" : raw.toString().trim();
            }
            if (dropdown == null) {
                return "";
            }
            CharSequence raw = dropdown.getText();
            String shown = raw == null ? "" : raw.toString().trim();
            if (shown.isEmpty()) {
                return "";
            }
            if (field.kind == Attrs.Kind.ENUM && field.values != null && field.valueLabels != null) {
                for (int i = 0; i < field.values.length && i < field.valueLabels.length; i++) {
                    if (context.getString(field.valueLabels[i]).equals(shown)) {
                        return field.values[i];
                    }
                }
                return "";
            }
            if (field.kind == Attrs.Kind.ENTITY_REF) {
                if (shown.equals(context.getString(R.string.attr_not_set))) {
                    return "";
                }
                String id = index.idFor(shown);
                return id == null ? "" : id;
            }
            return shown;
        }
    }

    private ActivityEntityEditBinding binding;
    private EntityEditViewModel viewModel;
    private String campaignId;
    private boolean creating;

    private final List<AttrRow> attrRows = new ArrayList<>();
    private NameIndex nameIndex = NameIndex.of(null);
    private List<EntityRoom> candidates = new ArrayList<>();
    private EntityType currentType = EntityType.NPC;

    private static final EntityType[] TYPE_VALUES = {
            EntityType.HERO, EntityType.NPC, EntityType.LOCATION, EntityType.ITEM};

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityEntityEditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        campaignId = getIntent() == null ? null : getIntent().getStringExtra(Extras.CAMPAIGN_ID);
        String entityId = getIntent() == null ? null : getIntent().getStringExtra(Extras.ENTITY_ID);
        String typeExtra = getIntent() == null ? null : getIntent().getStringExtra(Extras.ENTITY_TYPE);
        creating = getIntent() != null && getIntent().getBooleanExtra(Extras.CREATE_NEW, false);
        if (campaignId == null) {
            finish();
            return;
        }
        EntityType requested = EntityType.from(typeExtra);
        if (requested != null) {
            currentType = requested;
        }

        AppContainer container = App.container(this);
        viewModel = new ViewModelProvider(this, ViewModelFactory.of(
                () -> new EntityEditViewModel(container.entityRepository(), AppExecutors.get())))
                .get(EntityEditViewModel.class);
        viewModel.start(campaignId, entityId, currentType, creating);

        setSupportActionBar(binding.entityEditToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(creating
                    ? R.string.entity_edit_title_new : R.string.entity_edit_title_edit);
        }

        binding.entityEditType.setSimpleItems(typeLabels());
        binding.entityEditType.setOnItemClickListener((parent, view, position, id) -> {
            EntityType selected = selectedType();
            if (selected != currentType) {
                collect();
                currentType = selected;
                rebuildAttrRows();
            }
        });

        binding.entityEditSave.setOnClickListener(v -> {
            collect();
            viewModel.save();
        });
        binding.entityEditDelete.setOnClickListener(v -> confirmDelete());
        ViewUtils.show(binding.entityEditDelete, !creating);

        observe();
    }

    private String[] typeLabels() {
        String[] labels = new String[TYPE_VALUES.length];
        for (int i = 0; i < TYPE_VALUES.length; i++) {
            labels[i] = getString(Labels.entityType(TYPE_VALUES[i]));
        }
        return labels;
    }

    @NonNull
    private EntityType selectedType() {
        CharSequence shown = binding.entityEditType.getText();
        if (shown != null) {
            for (int i = 0; i < TYPE_VALUES.length; i++) {
                if (getString(Labels.entityType(TYPE_VALUES[i])).contentEquals(shown)) {
                    return TYPE_VALUES[i];
                }
            }
        }
        return currentType;
    }

    private void observe() {
        viewModel.state().observe(this, state -> {
            if (state == null) {
                return;
            }
            nameIndex = state.nameIndex;
            candidates = state.candidates;
            currentType = EntityType.fromOrNpc(state.entity.type);

            binding.entityEditNameInput.setText(state.entity.name);
            binding.entityEditSummaryInput.setText(state.entity.summary);
            binding.entityEditDescriptionInput.setText(state.entity.description);
            binding.entityEditTagsInput.setText(state.entity.tags == null
                    ? "" : TextUtils.join(", ", state.entity.tags));
            binding.entityEditType.setText(getString(Labels.entityType(currentType)), false);

            rebuildAttrRows();
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

    /** Пересобирает блок характеристик под текущий тип объекта. */
    private void rebuildAttrRows() {
        binding.entityEditAttrs.removeAllViews();
        attrRows.clear();

        Map<String, String> values = currentAttrValues();
        for (Attrs.Field field : Attrs.fieldsFor(currentType)) {
            binding.entityEditAttrs.addView(buildRow(field, values.get(field.key)));
        }
    }

    /** Значения, которые сейчас в форме (чтобы не потерять их при смене типа). */
    private Map<String, String> currentAttrValues() {
        Map<String, String> values = new LinkedHashMap<>();
        for (AttrRow row : attrRows) {
            String value = row.value(this, nameIndex);
            if (!value.isEmpty()) {
                values.put(row.field.key, value);
            }
        }
        return values;
    }

    private View buildRow(@NonNull Attrs.Field field, @Nullable String value) {
        TextInputLayout layout = new TextInputLayout(this);
        layout.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));
        layout.setHint(Labels.attrLabelText(this, field.key));

        if (field.kind == Attrs.Kind.ENUM || field.kind == Attrs.Kind.ENTITY_REF) {
            MaterialAutoCompleteTextView dropdown = new MaterialAutoCompleteTextView(this);
            dropdown.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            dropdown.setInputType(InputType.TYPE_NULL);
            dropdown.setSimpleItems(dropdownItems(field));
            dropdown.setText(displayValue(field, value), false);
            layout.addView(dropdown);
            attrRows.add(new AttrRow(field, null, dropdown));
        } else {
            TextInputEditText input = new TextInputEditText(this);
            input.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT));
            input.setInputType(field.kind == Attrs.Kind.NUMBER
                    ? InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL
                    : InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
            input.setText(value == null ? "" : value);
            layout.addView(input);
            attrRows.add(new AttrRow(field, input, null));
        }
        return layout;
    }

    @NonNull
    private String[] dropdownItems(@NonNull Attrs.Field field) {
        List<String> items = new ArrayList<>();
        if (field.kind == Attrs.Kind.ENUM && field.valueLabels != null) {
            for (int label : field.valueLabels) {
                items.add(getString(label));
            }
            return items.toArray(new String[0]);
        }
        items.add(getString(R.string.attr_not_set));
        for (EntityRoom candidate : candidates) {
            if (field.refType != null && !field.refType.name().equalsIgnoreCase(candidate.type)) {
                continue;
            }
            if (!TextUtils.isEmpty(candidate.name)) {
                items.add(candidate.name);
            }
        }
        return items.toArray(new String[0]);
    }

    @NonNull
    private String displayValue(@NonNull Attrs.Field field, @Nullable String value) {
        if (value == null || value.isEmpty()) {
            return field.kind == Attrs.Kind.ENTITY_REF ? getString(R.string.attr_not_set) : "";
        }
        if (field.kind == Attrs.Kind.ENUM && field.values != null && field.valueLabels != null) {
            for (int i = 0; i < field.values.length && i < field.valueLabels.length; i++) {
                if (field.values[i].equals(value)) {
                    return getString(field.valueLabels[i]);
                }
            }
            return value;
        }
        if (field.kind == Attrs.Kind.ENTITY_REF) {
            String name = nameIndex.nameFor(value);
            return name == null ? getString(R.string.attr_not_set) : name;
        }
        return value;
    }

    /** Переносит значения формы в ViewModel перед сохранением. */
    private void collect() {
        viewModel.setType(selectedType());
        CharSequence name = binding.entityEditNameInput.getText();
        viewModel.setName(name == null ? "" : name.toString());
        CharSequence summary = binding.entityEditSummaryInput.getText();
        viewModel.setSummary(summary == null ? "" : summary.toString());
        CharSequence description = binding.entityEditDescriptionInput.getText();
        viewModel.setDescription(description == null ? "" : description.toString());
        CharSequence tags = binding.entityEditTagsInput.getText();
        viewModel.setTagsCsv(tags == null ? "" : tags.toString());

        Map<String, String> attrs = new HashMap<>();
        for (AttrRow row : attrRows) {
            String value = row.value(this, nameIndex);
            if (!value.isEmpty()) {
                attrs.put(row.field.key, value);
            }
        }
        viewModel.setAttrs(attrs);
    }

    private void confirmDelete() {
        CharSequence name = binding.entityEditNameInput.getText();
        String title = name == null || name.toString().trim().isEmpty()
                ? getString(R.string.entity_detail_title) : name.toString().trim();
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
}
