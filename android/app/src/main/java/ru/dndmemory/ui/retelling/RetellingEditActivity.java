package ru.dndmemory.ui.retelling;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.text.TextUtils;
import android.text.method.LinkMovementMethod;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.List;
import java.util.Locale;

import ru.dndmemory.App;
import ru.dndmemory.AppContainer;
import ru.dndmemory.R;
import ru.dndmemory.data.local.entity.EntityRoom;
import ru.dndmemory.data.local.entity.RetellingDraftRoom;
import ru.dndmemory.data.model.EntityType;
import ru.dndmemory.databinding.ActivityRetellingEditBinding;
import ru.dndmemory.ui.common.ErrorMessages;
import ru.dndmemory.ui.common.Extras;
import ru.dndmemory.ui.common.NameIndex;
import ru.dndmemory.ui.common.Nav;
import ru.dndmemory.ui.common.ViewModelFactory;
import ru.dndmemory.ui.common.ViewUtils;
import ru.dndmemory.util.AppExecutors;
import ru.dndmemory.util.DateFormats;
import ru.dndmemory.util.WikiMarkdown;

/**
 * Редактор пересказа с предпросмотром разметки. Кнопка «Опубликовать» —
 * единственный путь, которым черновик покидает устройство.
 */
public class RetellingEditActivity extends AppCompatActivity {

    private ActivityRetellingEditBinding binding;
    private RetellingEditViewModel viewModel;
    private String campaignId;
    private boolean creating;
    private boolean previewShown;

    private NameIndex nameIndex = NameIndex.of(null);
    private List<EntityRoom> heroes = new ArrayList<>();

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityRetellingEditBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        campaignId = getIntent() == null ? null : getIntent().getStringExtra(Extras.CAMPAIGN_ID);
        String draftId = getIntent() == null ? null : getIntent().getStringExtra(Extras.DRAFT_ID);
        String retellingId = getIntent() == null ? null : getIntent().getStringExtra(Extras.RETELLING_ID);
        creating = getIntent() != null && getIntent().getBooleanExtra(Extras.CREATE_NEW, false);
        if (campaignId == null) {
            finish();
            return;
        }

        AppContainer container = App.container(this);
        viewModel = new ViewModelProvider(this, ViewModelFactory.of(
                () -> new RetellingEditViewModel(container.retellingRepository(),
                        container.entityRepository(), AppExecutors.get())))
                .get(RetellingEditViewModel.class);
        viewModel.start(campaignId, draftId, retellingId, creating);

        setSupportActionBar(binding.retellingEditToolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(creating || retellingId == null
                    ? R.string.retelling_edit_title_new : R.string.retelling_edit_title_edit);
        }

        binding.retellingEditDateInput.setOnClickListener(v -> showDatePicker());
        binding.retellingEditSaveDraft.setOnClickListener(v -> {
            collect();
            viewModel.saveDraft();
        });
        binding.retellingEditPublish.setOnClickListener(v -> {
            collect();
            if (TextUtils.isEmpty(text(binding.retellingEditTitleInput.getText()))
                    || TextUtils.isEmpty(text(binding.retellingEditBodyInput.getText()))) {
                ViewUtils.toast(this, R.string.error_required);
                return;
            }
            viewModel.publish();
        });
        binding.retellingEditPreviewToggle.setOnClickListener(v -> togglePreview());

        observe();
    }

    private void observe() {
        viewModel.state().observe(this, state -> {
            if (state == null) {
                return;
            }
            nameIndex = state.nameIndex;
            heroes = state.heroes;

            RetellingDraftRoom draft = state.draft;
            binding.retellingEditTitleInput.setText(draft.title);
            binding.retellingEditBodyInput.setText(draft.body);
            binding.retellingEditDateInput.setText(
                    TextUtils.isEmpty(draft.sessionDate) ? DateFormats.todayDate() : draft.sessionDate);
            setupAuthorDropdown(draft.authorEntityId);
        });

        viewModel.actionState().observe(this, resource -> {
            if (resource == null) {
                return;
            }
            ViewUtils.show(binding.retellingEditProgress, resource.isLoading());
            binding.retellingEditPublish.setEnabled(!resource.isLoading());
            binding.retellingEditSaveDraft.setEnabled(!resource.isLoading());
            if (resource.isError()) {
                ErrorMessages.show(binding.getRoot(), resource.error);
                viewModel.clearActionState();
            }
        });

        viewModel.finishRequested().observe(this, finished -> {
            if (!Boolean.TRUE.equals(finished)) {
                return;
            }
            boolean wasPublished = Boolean.TRUE.equals(viewModel.published().getValue());
            ViewUtils.toast(this, wasPublished
                    ? R.string.retelling_published_toast : R.string.retelling_draft_saved);
            finish();
        });
    }

    /** «От лица кого» — только герои партии (docs/api.md, раздел 7). */
    private void setupAuthorDropdown(@Nullable String authorEntityId) {
        List<String> labels = new ArrayList<>();
        labels.add(getString(R.string.retelling_author_none));
        for (EntityRoom hero : heroes) {
            if (!TextUtils.isEmpty(hero.name)) {
                labels.add(hero.name);
            }
        }
        binding.retellingEditAuthorInput.setSimpleItems(labels.toArray(new String[0]));
        String name = nameIndex.nameFor(authorEntityId);
        binding.retellingEditAuthorInput.setText(
                name == null ? getString(R.string.retelling_author_none) : name, false);
    }

    @Nullable
    private String selectedAuthorId() {
        CharSequence shown = binding.retellingEditAuthorInput.getText();
        if (shown == null) {
            return null;
        }
        String value = shown.toString().trim();
        if (value.isEmpty() || value.equals(getString(R.string.retelling_author_none))) {
            return null;
        }
        return nameIndex.idFor(value);
    }

    private void showDatePicker() {
        Calendar calendar = Calendar.getInstance();
        long parsed = DateFormats.parseIso(text(binding.retellingEditDateInput.getText()) + "T00:00:00.000Z");
        if (parsed > 0) {
            calendar.setTimeInMillis(parsed);
        }
        new DatePickerDialog(this, (view, year, month, dayOfMonth) -> {
            String value = String.format(Locale.US, "%04d-%02d-%02d", year, month + 1, dayOfMonth);
            binding.retellingEditDateInput.setText(value);
        }, calendar.get(Calendar.YEAR), calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)).show();
    }

    private void togglePreview() {
        previewShown = !previewShown;
        View bodyContainer = (View) binding.retellingEditBodyInput.getParent();
        ViewUtils.show(bodyContainer, !previewShown);
        ViewUtils.show(binding.retellingEditPreview, previewShown);
        binding.retellingEditPreviewToggle.setText(previewShown
                ? R.string.action_edit_body : R.string.action_preview);
        if (previewShown) {
            String source = text(binding.retellingEditBodyInput.getText());
            binding.retellingEditPreview.setText(WikiMarkdown.render(this, source,
                    name -> nameIndex.idFor(name), (targetId, name, resolved) -> {
                        if (resolved && targetId != null) {
                            Nav.openEntity(this, campaignId, targetId);
                        }
                    }));
            binding.retellingEditPreview.setMovementMethod(LinkMovementMethod.getInstance());
            binding.retellingEditPreview.setHighlightColor(Color.TRANSPARENT);
        }
    }

    private void collect() {
        viewModel.setTitle(text(binding.retellingEditTitleInput.getText()));
        viewModel.setBody(text(binding.retellingEditBodyInput.getText()));
        viewModel.setSessionDate(text(binding.retellingEditDateInput.getText()));
        viewModel.setAuthorEntityId(selectedAuthorId());
    }

    private static String text(@Nullable CharSequence value) {
        return value == null ? "" : value.toString().trim();
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
