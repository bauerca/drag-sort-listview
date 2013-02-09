package com.mobeta.android.demodslv;

import java.util.Arrays;
import java.util.ArrayList;

import android.support.v4.app.FragmentActivity;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v4.app.ListFragment;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MenuInflater;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListAdapter;
import com.mobeta.android.dslv.DragSortListView;
import com.mobeta.android.dslv.DragSortController;


public class TestBedDSLV extends FragmentActivity implements
RemoveModeDialog.RemoveOkListener,
DragInitModeDialog.DragOkListener,
EnablesDialog.EnabledOkListener
{

    private int mNumHeaders = 0;
    private int mNumFooters = 0;

    private int mDragStartMode = DragSortController.ON_DRAG;
    private boolean mRemoveEnabled = true;
    private int mRemoveMode = DragSortController.FLING_REMOVE;
    private boolean mSortEnabled = true;
    private boolean mDragEnabled = true;

    private String mTag = "dslvTag";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.test_bed_main);

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().add(R.id.test_bed, getNewDslvFragment(), mTag).commit();
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.mode_menu, menu);
        return true;
    }

    @Override
    public void onRemoveOkClick(int removeMode) {
        if (removeMode != mRemoveMode) {
            mRemoveMode = removeMode;
            getSupportFragmentManager().beginTransaction().replace(R.id.test_bed, getNewDslvFragment(), mTag).commit();
        }
    }

    @Override
    public void onDragOkClick(int dragStartMode) {
        mDragStartMode = dragStartMode;
        DSLVFragment f = (DSLVFragment) getSupportFragmentManager().findFragmentByTag(mTag);
        f.getController().setDragInitMode(dragStartMode);
    }

    @Override
    public void onEnabledOkClick(boolean drag, boolean sort, boolean remove) {
        mSortEnabled = sort;
        mRemoveEnabled = remove;
        mDragEnabled = drag;
        DSLVFragment f = (DSLVFragment) getSupportFragmentManager().findFragmentByTag(mTag);
        DragSortListView dslv = (DragSortListView) f.getListView();
        f.getController().setRemoveEnabled(remove);
        f.getController().setSortEnabled(sort);
        dslv.setDragEnabled(drag);
    }

    private Fragment getNewDslvFragment() {
        DSLVFragmentClicks f = DSLVFragmentClicks.newInstance(mNumHeaders, mNumFooters);
        f.removeMode = mRemoveMode;
        f.removeEnabled = mRemoveEnabled;
        f.dragStartMode = mDragStartMode;
        f.sortEnabled = mSortEnabled;
        f.dragEnabled = mDragEnabled;
        return f;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        FragmentTransaction transaction;
        DSLVFragment f = (DSLVFragment) getSupportFragmentManager().findFragmentByTag(mTag);
        DragSortListView dslv = (DragSortListView) f.getListView();
        DragSortController control = f.getController();

        switch (item.getItemId()) {
        case R.id.select_remove_mode:
            RemoveModeDialog rdialog = new RemoveModeDialog(mRemoveMode);
            rdialog.setRemoveOkListener(this);
            rdialog.show(getSupportFragmentManager(), "RemoveMode");
            return true;
        case R.id.select_drag_init_mode:
            DragInitModeDialog ddialog = new DragInitModeDialog(mDragStartMode);
            ddialog.setDragOkListener(this);
            ddialog.show(getSupportFragmentManager(), "DragInitMode");
            return true;
        case R.id.select_enables:
            EnablesDialog edialog = new EnablesDialog(mDragEnabled, mSortEnabled, mRemoveEnabled);
            edialog.setEnabledOkListener(this);
            edialog.show(getSupportFragmentManager(), "Enables");
            return true;
        case R.id.add_header:
            mNumHeaders++;

            transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.test_bed, getNewDslvFragment(), mTag);
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
            transaction.commit();
            return true;
        case R.id.add_footer:
            mNumFooters++;

            transaction = getSupportFragmentManager().beginTransaction();
            transaction.replace(R.id.test_bed, getNewDslvFragment(), mTag);
            transaction.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_FADE);
            transaction.commit();
            return true;
        default:
            return super.onOptionsItemSelected(item);
        }
    }
}
