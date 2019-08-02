package com.mobeta.android.demodslv;

import com.mobeta.android.dslv.DragSortController;

import android.support.v4.app.DialogFragment;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;

import com.mobeta.android.dslv.DragSortController;

/**
 * Simply passes remove mode back to OnOkListener
 */
public class RemoveModeDialog extends DialogFragment {

    private int mRemoveMode;

    private RemoveOkListener mListener;

    public RemoveModeDialog() {
        super();
        mRemoveMode = DragSortController.FLING_REMOVE;
    }

    public RemoveModeDialog(int inRemoveMode) {
        super();
        mRemoveMode = inRemoveMode;
    }

    public interface RemoveOkListener {
        public void onRemoveOkClick(int removeMode);
    }

    public void setRemoveOkListener(RemoveOkListener l) {
        mListener = l;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        // Set the dialog title
        builder.setTitle(R.string.select_remove_mode)
                .setSingleChoiceItems(R.array.remove_mode_labels, mRemoveMode,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                mRemoveMode = which;
                            }
                        })
                // Set the action buttons
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        if (mListener != null) {
                            mListener.onRemoveOkClick(mRemoveMode);
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
