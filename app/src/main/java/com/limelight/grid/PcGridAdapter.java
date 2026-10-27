package com.limelight.grid;

import android.content.Context;
import android.view.View;
import android.graphics.drawable.GradientDrawable;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.limelight.PcView;
import com.limelight.R;
import com.limelight.nvstream.http.ComputerDetails;
import com.limelight.nvstream.http.PairingManager;
import com.limelight.preferences.PreferenceConfiguration;

import java.util.Collections;
import java.util.Comparator;

public class PcGridAdapter extends GenericGridAdapter<PcView.ComputerObject> {

    // The PC shown in the big card, highlighted in the row
    private String heroUuid;

    public void setHeroUuid(String uuid) {
        heroUuid = uuid;
    }

    public PcGridAdapter(Context context, PreferenceConfiguration prefs) {
        super(context, getLayoutIdForPreferences(prefs));
    }

    private static int getLayoutIdForPreferences(PreferenceConfiguration prefs) {
        return R.layout.pc_grid_item;
    }

    public void updateLayoutWithPreferences(Context context, PreferenceConfiguration prefs) {
        // This will trigger the view to reload with the new layout
        setLayoutId(getLayoutIdForPreferences(prefs));
    }

    public void addComputer(PcView.ComputerObject computer) {
        itemList.add(computer);
        sortList();
    }

    private void sortList() {
        Collections.sort(itemList, new Comparator<PcView.ComputerObject>() {
            @Override
            public int compare(PcView.ComputerObject lhs, PcView.ComputerObject rhs) {
                return lhs.details.name.toLowerCase().compareTo(rhs.details.name.toLowerCase());
            }
        });
    }

    public boolean removeComputer(PcView.ComputerObject computer) {
        return itemList.remove(computer);
    }

    @Override
    public void populateView(View parentView, ImageView imgView, RelativeLayout gridMask, ProgressBar prgView, TextView txtView, ImageView overlayView, PcView.ComputerObject obj) {
        ComputerDetails details = obj.details;
        boolean online = details.state == ComputerDetails.State.ONLINE;
        boolean unknown = details.state == ComputerDetails.State.UNKNOWN;

        imgView.setImageResource(R.drawable.ic_si_tower);
        imgView.setVisibility(unknown ? View.INVISIBLE : View.VISIBLE);
        imgView.setAlpha(online ? 1.0f : 0.5f);
        prgView.setVisibility(unknown ? View.VISIBLE : View.GONE);

        txtView.setText(details.name);
        txtView.setAlpha(online ? 1.0f : 0.6f);

        int statusText;
        int dotColor;
        if (unknown) {
            statusText = R.string.si_short_checking;
            dotColor = 0xFF98A2B3;
        }
        else if (!online) {
            boolean canWake = PcView.canWake(details);
            statusText = canWake ? R.string.si_short_asleep : R.string.si_short_offline;
            dotColor = canWake ? 0xFF98A2B3 : 0xFF4A5463;
        }
        else if (details.pairState != PairingManager.PairState.PAIRED) {
            statusText = R.string.si_short_unpaired;
            dotColor = 0xFFF59E0B;
        }
        else if (details.runningGameId != 0) {
            statusText = R.string.si_short_session;
            dotColor = 0xFFF59E0B;
        }
        else {
            statusText = R.string.si_short_awake;
            dotColor = 0xFF2DD4BF;
        }

        TextView statusView = parentView.findViewById(R.id.grid_status);
        statusView.setText(statusText);

        GradientDrawable dot = new GradientDrawable();
        dot.setShape(GradientDrawable.OVAL);
        dot.setColor(dotColor);
        overlayView.setImageDrawable(dot);
        overlayView.setVisibility(View.VISIBLE);

        parentView.setActivated(details.uuid.equals(heroUuid));
    }
}
