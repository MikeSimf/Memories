package ru.dndmemory.ui.campaign;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import ru.dndmemory.data.model.EntityType;
import ru.dndmemory.ui.entity.EntityListFragment;
import ru.dndmemory.ui.members.MembersFragment;
import ru.dndmemory.ui.retelling.RetellingListFragment;

/** Шесть вкладок кампании: Герои, Персонажи, Локации, Предметы, Пересказы, Участники. */
public class CampaignPagerAdapter extends FragmentStateAdapter {

    public static final int TAB_COUNT = 6;

    public CampaignPagerAdapter(@NonNull FragmentActivity activity) {
        super(activity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 0:
                return EntityListFragment.newInstance(EntityType.HERO);
            case 2:
                return EntityListFragment.newInstance(EntityType.LOCATION);
            case 3:
                return EntityListFragment.newInstance(EntityType.ITEM);
            case 4:
                return RetellingListFragment.newInstance();
            case 5:
                return MembersFragment.newInstance();
            case 1:
            default:
                return EntityListFragment.newInstance(EntityType.NPC);
        }
    }

    @Override
    public int getItemCount() {
        return TAB_COUNT;
    }
}
