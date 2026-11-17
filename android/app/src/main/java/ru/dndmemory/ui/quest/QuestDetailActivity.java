package ru.dndmemory.ui.quest;

import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.widget.Toolbar;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;
import ru.dndmemory.App;
import ru.dndmemory.R;
import ru.dndmemory.data.remote.dto.QuestDto;
import ru.dndmemory.ui.common.BaseCampaignActivity;
import ru.dndmemory.ui.common.Nav;
import ru.dndmemory.ui.common.ViewUtils;

/** Карточка квеста: описание и комментарии. */
public class QuestDetailActivity extends BaseCampaignActivity {

    private String questId;
    private Toolbar toolbar;
    private LinearLayout content;
    private QuestDto quest;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!initCampaign()) return;
        questId = getIntent() == null ? null : getIntent().getStringExtra("questId");
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        toolbar = new Toolbar(this);
        toolbar.setNavigationIcon(androidx.appcompat.R.drawable.abc_ic_ab_back_material);
        toolbar.setNavigationOnClickListener(v -> finish());
        root.addView(toolbar);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(24, 12, 24, 24);
        root.addView(content);
        setContentView(root);
        load();
    }

    private void load() {
        App.container(this).apiService().quest(campaignId, questId).enqueue(new Callback<QuestDto>() {
            @Override public void onResponse(@NonNull Call<QuestDto> call, @NonNull Response<QuestDto> response) {
                runOnUiThread(() -> { quest = response.body(); render(); });
            }
            @Override public void onFailure(@NonNull Call<QuestDto> call, @NonNull Throwable t) {
                runOnUiThread(() -> ViewUtils.toast(QuestDetailActivity.this, R.string.maps_load_failed));
            }
        });
    }

    private void render() {
        if (quest == null) return;
        toolbar.setTitle(quest.name);
        content.removeAllViews();
        TextView description = new TextView(this);
        description.setText(quest.description == null ? "" : quest.description);
        description.setTextSize(16);
        content.addView(description);
        TextView header = new TextView(this);
        header.setText(R.string.quest_comments);
        header.setTextSize(18);
        header.setPadding(0, 24, 0, 8);
        content.addView(header);
        for (QuestDto.Comment comment : quest.comments) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(android.view.Gravity.CENTER_VERTICAL);
            TextView author = new TextView(this);
            author.setText((comment.createdByName == null ? "" : comment.createdByName) + " · " + comment.text);
            author.setPadding(0, 8, 8, 8);
            row.addView(author, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
            LinearLayout actions = new LinearLayout(this);
            actions.setOrientation(LinearLayout.VERTICAL);
            android.widget.ImageButton edit = iconButton(R.drawable.ic_edit, R.string.quest_comment_edit);
            edit.setOnClickListener(v -> editComment(comment));
            android.widget.ImageButton delete = iconButton(R.drawable.ic_delete, R.string.quest_comment_delete);
            delete.setOnClickListener(v -> deleteComment(comment));
            actions.addView(edit);
            actions.addView(delete);
            row.addView(actions);
            content.addView(row);
        }
        Button add = new Button(this);
        add.setText(R.string.quest_comment_add);
        add.setOnClickListener(v -> addComment());
        content.addView(add);
    }

    private android.widget.ImageButton iconButton(int icon, int description) {
        android.widget.ImageButton button = new android.widget.ImageButton(this);
        button.setImageResource(icon);
        button.setContentDescription(getString(description));
        button.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        int pad = (int) (8 * getResources().getDisplayMetrics().density);
        button.setPadding(pad, pad, pad, pad);
        return button;
    }

    private void addComment() {
        EditText text = new EditText(this);
        text.setMinLines(3);
        text.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        new AlertDialog.Builder(this)
                .setTitle(R.string.quest_comment_add)
                .setView(text)
                .setPositiveButton(R.string.action_save, (d, w) -> {
                    java.util.Map<String, Object> body = new java.util.HashMap<>();
                    body.put("text", text.getText().toString());
                    App.container(this).apiService().addQuestComment(campaignId, questId, body)
                            .enqueue(new Callback<QuestDto.Comment>() {
                                @Override public void onResponse(@NonNull Call<QuestDto.Comment> call, @NonNull Response<QuestDto.Comment> response) { runOnUiThread(QuestDetailActivity.this::load); }
                                @Override public void onFailure(@NonNull Call<QuestDto.Comment> call, @NonNull Throwable t) { }
                            });
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void editComment(QuestDto.Comment comment) {
        EditText text = new EditText(this);
        text.setText(comment.text);
        text.setMinLines(3);
        text.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        new AlertDialog.Builder(this)
                .setTitle(R.string.quest_comment_edit)
                .setView(text)
                .setPositiveButton(R.string.action_save, (d, w) -> {
                    java.util.Map<String, Object> body = new java.util.HashMap<>();
                    body.put("text", text.getText().toString());
                    App.container(this).apiService().updateQuestComment(campaignId, questId, comment.id, body)
                            .enqueue(new Callback<Void>() {
                                @Override public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) { runOnUiThread(QuestDetailActivity.this::load); }
                                @Override public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) { }
                            });
                })
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void deleteComment(QuestDto.Comment comment) {
        new AlertDialog.Builder(this)
                .setMessage(R.string.quest_comment_delete_confirm)
                .setPositiveButton(R.string.quest_comment_delete, (d, w) -> doDeleteComment(comment))
                .setNegativeButton(R.string.action_cancel, null)
                .show();
    }

    private void doDeleteComment(QuestDto.Comment comment) {
        App.container(this).apiService().deleteQuestComment(campaignId, questId, comment.id)
                .enqueue(new Callback<Void>() {
                    @Override public void onResponse(@NonNull Call<Void> call, @NonNull Response<Void> response) { runOnUiThread(QuestDetailActivity.this::load); }
                    @Override public void onFailure(@NonNull Call<Void> call, @NonNull Throwable t) { }
                });
    }
}
