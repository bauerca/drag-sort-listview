package com.mobeta.android.demodslv;

public class Fling extends BasicDSLV {
    @Override
    protected int getLayout() {
        return R.layout.main_fling;
    }
	
	@Override
    protected int getItemLayout() {
        return R.layout.list_item_handle_left;
    }
}
