package bin.xposed.Unblock163MusicClient.ui;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.preference.EditTextPreference;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.AttributeSet;
import android.util.Patterns;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

// copy and modify from http://stackoverflow.com/questions/4762027/edittextpreference-disable-buttons

public class IPv4EditTextPreference extends EditTextPreference {

    EditTextWatcher m_watcher = new EditTextWatcher();

    public IPv4EditTextPreference(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public IPv4EditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public IPv4EditTextPreference(Context context) {
        super(context);
    }

    protected boolean onCheckValue(String value) {
        return Patterns.IP_ADDRESS.matcher(value).matches();
    }

    protected void onEditTextChanged() {
        boolean enable = onCheckValue(getEditText().getText().toString());
        Dialog dlg = getDialog();
        if (dlg instanceof AlertDialog) {
            AlertDialog alertDlg = (AlertDialog) dlg;
            Button btn = alertDlg.getButton(AlertDialog.BUTTON_POSITIVE);
            btn.setEnabled(enable);
        }
    }

    @Override
    protected void showDialog(Bundle state) {
        super.showDialog(state);

        this.getEditText().setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                EditText editText = (EditText) v;
                editText.setSelection(editText.getText().length());
            }
        });

        getEditText().removeTextChangedListener(m_watcher);
        getEditText().addTextChangedListener(m_watcher);
        onEditTextChanged();
    }

    @Override
    protected void onDialogClosed(boolean positiveResult) {
        super.onDialogClosed(positiveResult);
        setSummary(getSummary());
    }

    @Override
    public CharSequence getSummary() {
        return this.getText();
    }

    private class EditTextWatcher implements TextWatcher {
        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void beforeTextChanged(CharSequence s, int start, int before, int count) {
        }

        @Override
        public void afterTextChanged(Editable s) {
            onEditTextChanged();
        }
    }
}