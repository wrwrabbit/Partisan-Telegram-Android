package org.telegram.messenger.partisan.masked_ptg.note;

import static org.telegram.messenger.AndroidUtilities.dp;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.RoundRectShape;
import android.view.View;
import android.widget.FrameLayout;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import org.bitbucket.cowwoc.diffmatchpatch.DiffMatchPatch;
import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.LocaleController;
import org.telegram.messenger.R;
import org.telegram.messenger.partisan.masked_ptg.AbstractMaskedPasscodeScreen;
import org.telegram.messenger.partisan.masked_ptg.MaskedPtgConfig;
import org.telegram.messenger.partisan.masked_ptg.PasscodeEnteredDelegate;
import org.telegram.messenger.partisan.masked_ptg.TutorialType;
import org.telegram.ui.ActionBar.AlertDialog;
import org.telegram.ui.ActionBar.Theme;
import org.telegram.ui.Components.LayoutHelper;

import java.util.LinkedList;

public class NotePasscodeScreen extends AbstractMaskedPasscodeScreen
        implements NoteListSubscreen.NoteListSubscreenDelegate
        , EditNoteSubcreen.NoteListSubscreenDelegate {

    private Note currentNote;
    private int currentNotePos = -1;

    private FrameLayout backgroundFrameLayout;
    private NoteListSubscreen noteListSubscreen;
    private EditNoteSubcreen editNoteSubscreen;

    public NotePasscodeScreen(Context context, PasscodeEnteredDelegate delegate, boolean unlockingApp) {
        super(context, delegate, unlockingApp);
        NoteStorage.loadNotes();
    }

    @Override
    public View createView() {
        createBackgroundFrameLayout();

        createNoteListSubscreen();
        createSingleNoteSubscreen();
        return backgroundFrameLayout;
    }

    private void createNoteListSubscreen() {
        noteListSubscreen = new NoteListSubscreen(context, this);
        FrameLayout.LayoutParams params = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT);
        params.setMargins(0, AndroidUtilities.statusBarHeight, 0, AndroidUtilities.navigationBarHeight);
        backgroundFrameLayout.addView(noteListSubscreen, params);
    }

    private void createSingleNoteSubscreen() {
        editNoteSubscreen = new EditNoteSubcreen(context, this);
        FrameLayout.LayoutParams params = LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT);
        params.setMargins(0, AndroidUtilities.statusBarHeight, 0, AndroidUtilities.navigationBarHeight);
        editNoteSubscreen.setVisibility(View.GONE);
        backgroundFrameLayout.addView(editNoteSubscreen, params);
    }

    @SuppressLint("ClickableViewAccessibility")
    private void createBackgroundFrameLayout() {
        backgroundFrameLayout = new FrameLayout(context);
        backgroundFrameLayout.setWillNotDraw(false);
        backgroundFrameLayout.setBackgroundColor(Colors.screenBackgroundColor);
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int topInset = AndroidUtilities.statusBarHeight;
        int bottomInset = AndroidUtilities.navigationBarHeight;
        WindowInsetsCompat insetsCompat = ViewCompat.getRootWindowInsets(backgroundFrameLayout);
        if (insetsCompat != null) {
            Insets systemInsets = insetsCompat.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.systemBars());
            topInset = systemInsets.top;
            bottomInset = systemInsets.bottom;
        }
        updateSubscreenMargins(noteListSubscreen, topInset, bottomInset);
        updateSubscreenMargins(editNoteSubscreen, topInset, bottomInset);
    }

    private void updateSubscreenMargins(View subscreen, int topInset, int bottomInset) {
        FrameLayout.LayoutParams params = (FrameLayout.LayoutParams) subscreen.getLayoutParams();
        params.topMargin = topInset;
        params.bottomMargin = bottomInset;
    }

    @Override
    public void onShow(boolean fingerprint, boolean animated, TutorialType tutorialType) {
        Activity parentActivity = AndroidUtilities.findActivity(context);
        if (parentActivity != null) {
            View currentFocus = parentActivity.getCurrentFocus();
            if (currentFocus != null) {
                currentFocus.clearFocus();
                AndroidUtilities.hideKeyboard(parentActivity.getCurrentFocus());
            }
        }
        setTutorial(tutorialType);
    }

    private void setTutorial(TutorialType tutorialType) {
        this.tutorialType = tutorialType;
        noteListSubscreen.setTutorial(tutorialType != TutorialType.DISABLED);
        editNoteSubscreen.setTutorial(tutorialType != TutorialType.DISABLED);
        if (tutorialType == TutorialType.FULL) {
            createInstructionDialog().show();
        }
    }

    private AlertDialog createInstructionDialog() {
        String message = LocaleController.getString(R.string.NotePasscodeScreen_Instruction);
        return createMaskedPasscodeScreenInstructionDialog(message, 0);
    }

    @Override
    public void onNoteClicked(int pos) {
        currentNotePos = pos;
        editNoteSubscreen.bindNote(NoteStorage.getNote(pos));
        showEditNoteSubscreen();
    }

    @Override
    public void onNoteSelected(int pos) {
        AlertDialog.Builder dialogBuilder = new AlertDialog.Builder(context, key -> {
            if (key == Theme.key_dialogBackground) {
                return Colors.dialogBackgroundColor;
            } else if (key == Theme.key_dialogTextBlack) {
                return Colors.noteTitleColor;
            } else if (key == Theme.key_dialogButton) {
                return getPrimaryColor();
            }
            return getPrimaryColor();
        });
        dialogBuilder.setTitle(LocaleController.getString(R.string.AppName));
        dialogBuilder.setMessage("Delete the note?");
        dialogBuilder.setPositiveButton(LocaleController.getString("Delete", R.string.Delete), (dlg, which) -> {
            NoteStorage.removeNote(pos);
            noteListSubscreen.notifyNoteRemoved(pos);
        });
        dialogBuilder.setNeutralButton(LocaleController.getString("Cancel", R.string.Cancel), null);
        AlertDialog dialog = dialogBuilder.create();
        int rad = dp(9);
        float[] radii = new float[]{rad, rad, rad, rad, rad, rad, rad, rad};
        ShapeDrawable backgroundDrawable = new ShapeDrawable(new RoundRectShape(radii, null, null));
        backgroundDrawable.getPaint().setColor(Colors.cellBackgroundColor);
        dialog.getWindow().setBackgroundDrawable(backgroundDrawable);
        dialog.show();
    }

    @Override
    public void onAddNote() {
        currentNotePos = -1;
        editNoteSubscreen.bindNote(new Note());
        showEditNoteSubscreen();
    }

    @Override
    public void onNoteEdited(Note note) {
        currentNote = note; // If passcode fails, onPasscodeError will be called. Otherwise the note with a correct passcode will not be added.
        delegate.passcodeEntered(note.title);
        if (currentNote != null) {
            removeNotesWithTitleLikePassword(note.title);
            currentNote = null;
        }
        hideEditNoteSubscreen();
    }

    private void removeNotesWithTitleLikePassword(String password) {
        for (int i = NoteStorage.getNotesCount() - 1; i >= 0; i--) {
            Note note = NoteStorage.getNote(i);
            if (note.title.equals(password) || note.description.isEmpty() && stringAlmostEqualToPassword(note.title, password)) {
                NoteStorage.removeNote(i);
                noteListSubscreen.notifyNoteRemoved(i);
            }
        }
    }

    private boolean stringAlmostEqualToPassword(String str, String password) {
        if (str.length() > password.length() * 1.25) {
            return false;
        }
        DiffMatchPatch diffMatchPatch = new DiffMatchPatch();
        int equalLength = diffMatchPatch.diffMain(str.toLowerCase(), password.toLowerCase(), false)
                .stream()
                .filter(diff -> diff.operation == DiffMatchPatch.Operation.EQUAL)
                .mapToInt(diff -> diff.text.length())
                .sum();
        return equalLength >= password.length() * 0.75; // The string contains 75% of the password
    }

    private void hideEditNoteSubscreen() {
        showEditNoteSubscreen(false);
    }

    private void showEditNoteSubscreen() {
        showEditNoteSubscreen(true);
    }

    private void showEditNoteSubscreen(boolean show) {
        noteListSubscreen.setVisibility(show ? View.GONE : View.VISIBLE);
        editNoteSubscreen.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onPasscodeError() {
        if (currentNotePos != -1) {
            noteListSubscreen.notifyNoteEdited(currentNotePos);
            NoteStorage.saveNotes();
        } else {
            if (!currentNote.title.isEmpty() || !currentNote.description.isEmpty()) {
                NoteStorage.addNote(currentNote);
                noteListSubscreen.notifyNoteAdded();
            }
        }
        currentNote = null;
        if (tutorialType != TutorialType.DISABLED) {
            AlertDialog.Builder builder = new AlertDialog.Builder(context);
            builder.setTitle(LocaleController.getString(R.string.MaskedPasscodeScreen_Tutorial));
            builder.setMessage(LocaleController.getString(R.string.MaskedPasscodeScreen_WrongPasscode));
            builder.setNegativeButton(LocaleController.getString(R.string.OK), null);
            builder.create().show();
        }
    }

    @Override
    public boolean onBackPressed() {
        if (editNoteSubscreen.getVisibility() == View.VISIBLE) {
            AndroidUtilities.runOnUIThread(this::hideEditNoteSubscreen, 200);
            return false;
        } else {
            return true;
        }
    }

    private int getPrimaryColor() {
        return MaskedPtgConfig.getPrimaryColor(context);
    }
}
