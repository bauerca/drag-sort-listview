package com.mobeta.android.demodslv;

import com.mobeta.android.dslv.DragSortListView;
import com.mobeta.android.dslv.DragSortController;

import java.util.ArrayList;

import android.support.v4.app.DialogFragment;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

public class EnablesDialog extends DialogFragment {

    private boolean[] mEnabled;

    private EnabledOkListener mListener;

    public EnablesDialog() {
        super();
        mEnabled = new boolean[3];
        mEnabled[0] = true;
        mEnabled[1] = true;
        mEnabled[2] = false;
    }

    public EnablesDialog(boolean drag, boolean sort, boolean remove) {
        super();
        mEnabled = new boolean[3];
        mEnabled[0] = drag;
        mEnabled[1] = sort;
        mEnabled[2] = remove;
    }

    public interface EnabledOkListener {
        public void onEnabledOkClick(boolean drag, boolean sort, boolean remove);
    }

    public void setEnabledOkListener(EnabledOkListener l) {
        mListener = l;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // Set the dialog title
        builder.setTitle(R.string.select_remove_mode)
                .setMultiChoiceItems(R.array.enables_labels, mEnabled,
                        new DialogInterface.OnMultiChoiceClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which, boolean isChecked) {
                                mEnabled[which] = isChecked;
                            }
                        })
                // Set the action buttons
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        if (mListener != null) {
                            mListener.onEnabledOkClick(mEnabled[0], mEnabled[1], mEnabled[2]);
                        }
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                    }
                });
    
        return builder.create();
    }
}
