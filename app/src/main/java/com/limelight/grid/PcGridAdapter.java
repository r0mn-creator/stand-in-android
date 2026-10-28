package com.limelight.grid;

import android.content.Context;
import android.content.res.ColorStateList;
import android.view.View;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.limelight.PcView;
import com.limelight.R;
import com.limelight.preferences.PreferenceConfiguration;
import com.limelight.ui.si.Si;
import com.limelight.ui.si.SiPc;

import java.util.Collections;
import java.util.Comparator;

/** The PCs on the home screen, drawn as tiles (DESIGN.md "PC tile"). */
public class PcGridAdapter extends GenericGridAdapter<PcView.ComputerObject> {

    // The PC shown in the hero card, highlighted in the row
    private String heroUuid;
    private String wakingUuid;

    public void setHeroUuid(String uuid) {
        heroUuid = uuid;
    }

    public void setWakingUuid(String uuid) {
        wakingUuid = uuid;
    }

    public PcGridAdapter(Context context, PreferenceConfiguration prefs) {
        super(context, R.layout.pc_grid_item);
    }

    public void updateLayoutWithPreferences(Context context, PreferenceConfiguration prefs) {
        setLayoutId(R.layout.pc_grid_item);
    }

    public void addComputer(PcView.ComputerObject computer) {
        itemList.add(computer);
        sortList();
    }

    public void sortList() {
        Collections.sort(itemList, new Comparator<PcView.ComputerObject>() {
            @Override
            public int compare(PcView.ComputerObject lhs, PcView.ComputerObject rhs) {
                return SiPc.name(context, lhs.details).toLowerCase().compareTo(SiPc.name(context, rhs.details).toLowerCase());
            }
        });
    }

    public boolean removeComputer(PcView.ComputerObject computer) {
        return itemList.remove(computer);
    }

    @Override
    public void populateView(View parentView, ImageView imgView, RelativeLayout gridMask, ProgressBar prgView, TextView txtView, ImageView overlayView, PcView.ComputerObject obj) {
        SiPc.State state = SiPc.of(obj.details, obj.details.uuid.equals(wakingUuid));
        boolean dim = state == SiPc.State.OFFLINE;

        imgView.setImageTintList(ColorStateList.valueOf(Si.color(context, dim ? R.color.si_subtle : R.color.si_text)));
        txtView.setText(SiPc.name(context, obj.details));
        txtView.setTextColor(Si.color(context, dim ? R.color.si_muted : R.color.si_text));

        boolean busy = state == SiPc.State.CHECKING || state == SiPc.State.WAKING;
        TextView statusView = parentView.findViewById(R.id.grid_status);
        statusView.setText(SiPc.shortText(state));
        overlayView.setVisibility(busy ? View.INVISIBLE : View.VISIBLE);
        overlayView.setImageTintList(ColorStateList.valueOf(Si.color(context, SiPc.dotColor(state))));
        parentView.findViewById(R.id.si_status_spinner).setVisibility(busy ? View.VISIBLE : View.GONE);

        parentView.setActivated(obj.details.uuid.equals(heroUuid));
        parentView.setContentDescription(SiPc.name(context, obj.details) + ", " + context.getString(SiPc.shortText(state)));
    }
}
