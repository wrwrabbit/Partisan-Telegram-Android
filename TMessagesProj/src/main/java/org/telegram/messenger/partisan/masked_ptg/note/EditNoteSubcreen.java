package org.telegram.messenger.partisan.masked_ptg.note;

import static org.telegram.messenger.AndroidUtilities.dp;
import static org.telegram.ui.Components.LayoutHelper.MATCH_PARENT;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.StateSet;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.RelativeLayout;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.partisan.masked_ptg.MaskedPtgConfig;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.EditTextBoldCursor;

class EditNoteSubcreen extends RelativeLayout {
    interface NoteListSubscreenDelegate {
        void onNoteEdited(Note note);
    }

    private Note note;
    private final NoteListSubscreenDelegate delegate;

    private EditTextBoldCursor titleEditText;
    private EditTextBoldCursor descriptionEditText;
    private Button acceptButton;

    private boolean tutorial;
    private View titleTutorialArrow;
    private View acceptButtonTutorialArrow;

    EditNoteSubcreen(Context context, NoteListSubscreenDelegate delegate) {
        super(context);
        this.delegate = delegate;

        createAcceptButton();
        createAcceptButtonTipArrow();
        createTitleEditText();
        createTitleTipArrow();
        createDescriptionEditText();
    }

    private void createAcceptButton() {
        acceptButton = new Button(getContext());
        acceptButton.setTextColor(Color.BLACK);
        acceptButton.setTextSize(20);
        acceptButton.setId(generateViewId());

        GradientDrawable shape = new GradientDrawable();
        shape.setShape(GradientDrawable.OVAL);
        shape.setColor(getPrimaryColor());
        acceptButton.setBackground(Theme.AdaptiveRipple.filledCircle(shape, getPrimaryColor(), -1));

        LayoutParams relativeParams = new LayoutParams(dp(48), dp(48));
        relativeParams.addRule(ALIGN_PARENT_TOP);
        relativeParams.addRule(ALIGN_PARENT_END);
        acceptButton.setOnClickListener((v) -> {
            AndroidUtilities.hideKeyboard(titleEditText);
            AndroidUtilities.hideKeyboard(descriptionEditText);
            note.title = titleEditText.getText().toString();
            note.description = descriptionEditText.getText().toString();
            delegate.onNoteEdited(note);
        });
        addView(acceptButton, relativeParams);
    }

    private void createAcceptButtonTipArrow() {
        LayoutParams relativeParams = new LayoutParams(dp(48), dp(48));
        relativeParams.addRule(BELOW, acceptButton.getId());
        relativeParams.addRule(LEFT_OF, acceptButton.getId());

        acceptButtonTutorialArrow = new TutorialArrow(getContext(), 45, 0, dp(22));
        acceptButtonTutorialArrow.setLayoutParams(relativeParams);
        acceptButtonTutorialArrow.setVisibility(View.GONE);
        addView(acceptButtonTutorialArrow);
    }

    private void createTitleEditText() {
        titleEditText = new EditTextBoldCursor(getContext());
        titleEditText.setTextColor(Colors.noteTitleColor);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ColorStateList colorStateList = new ColorStateList(
                    new int[][]{StateSet.WILD_CARD},
                    new int[]{getPrimaryColor()}
            );
            titleEditText.setBackgroundTintList(colorStateList);
        }
        titleEditText.setId(generateViewId());
        titleEditText.setHint(LocaleController.getString(R.string.NotePasscodeScreen_NoteTitle));
        titleEditText.setHintTextColor(Colors.noteTitleHintColor);
        titleEditText.setCursorColor(getPrimaryColor());
        titleEditText.setHandlesColor(getPrimaryColor());
        titleEditText.setMaxLines(1);
        titleEditText.setSingleLine();
        titleEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 32);
        titleEditText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (tutorial) {
                    if (s.length() == 0) {
                        titleTutorialArrow.setVisibility(View.VISIBLE);
                        acceptButtonTutorialArrow.setVisibility(View.GONE);

                    } else {
                        titleTutorialArrow.setVisibility(View.GONE);
                        acceptButtonTutorialArrow.setVisibility(View.VISIBLE);
                    }
                } else {
                    acceptButtonTutorialArrow.setVisibility(View.GONE);
                    titleTutorialArrow.setVisibility(View.GONE);
                }
            }
        });
        LayoutParams relativeParams = new LayoutParams(MATCH_PARENT, dp(48));
        relativeParams.addRule(ALIGN_PARENT_TOP);
        relativeParams.addRule(ALIGN_PARENT_START);
        relativeParams.addRule(LEFT_OF, acceptButton.getId());
        addView(titleEditText, relativeParams);
    }

    private void createTitleTipArrow() {
        LayoutParams relativeParams = new LayoutParams(dp(48), dp(48));
        relativeParams.addRule(BELOW, titleEditText.getId());
        relativeParams.addRule(ALIGN_START, titleEditText.getId());
        relativeParams.addRule(ALIGN_END, titleEditText.getId());

        titleTutorialArrow = new TutorialArrow(getContext(), 90, -dp(11), dp(11));
        titleTutorialArrow.setLayoutParams(relativeParams);
        addView(titleTutorialArrow);
    }

    private void createDescriptionEditText() {
        descriptionEditText = new EditTextBoldCursor(getContext());
        descriptionEditText.setTextColor(Colors.descriptionColor);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            ColorStateList colorStateList = new ColorStateList(
                    new int[][]{StateSet.WILD_CARD},
                    new int[]{getPrimaryColor()}
            );
            descriptionEditText.setBackgroundTintList(colorStateList);
        }
        descriptionEditText.setId(generateViewId());
        descriptionEditText.setHint(LocaleController.getString(R.string.NotePasscodeScreen_NoteDescription));
        descriptionEditText.setHintTextColor(Colors.descriptionHintColor);
        descriptionEditText.setCursorColor(getPrimaryColor());
        descriptionEditText.setHandlesColor(getPrimaryColor());
        descriptionEditText.setSingleLine(false);
        descriptionEditText.setTextSize(TypedValue.COMPLEX_UNIT_DIP, 18);
        descriptionEditText.setGravity(Gravity.TOP);
        LayoutParams relativeParams = new LayoutParams(MATCH_PARENT, MATCH_PARENT);
        relativeParams.addRule(BELOW, titleEditText.getId());
        relativeParams.addRule(ALIGN_PARENT_START);
        relativeParams.addRule(LEFT_OF, acceptButton.getId());
        addView(descriptionEditText, relativeParams);
    }

    void bindNote(Note note) {
        this.note = note;
        titleEditText.setText(note.title);
        descriptionEditText.setText(note.description);
        if (note.title == null && note.description == null) {
            acceptButton.setText("+");
        } else {
            acceptButton.setText("+");
        }
    }

    void setTutorial(boolean tutorial) {
        this.tutorial = tutorial;
        titleTutorialArrow.setVisibility(tutorial ? View.VISIBLE : View.GONE);
        acceptButtonTutorialArrow.setVisibility(View.GONE);
    }

    private int getPrimaryColor() {
        return MaskedPtgConfig.getPrimaryColor(getContext());
    }
}
