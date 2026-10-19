package ru.dndmemory.ui.campaign;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager2.adapter.FragmentStateAdapter;

import ru.dndmemory.data.model.EntityType;
import ru.dndmemory.ui.entity.EntityListFragment;
import ru.dndmemory.ui.material.MaterialsFragment;

/**
 * Четыре вкладки кампании: Персонажи (герои и НПС вместе), Локации, Предметы, Материалы.
 * Пересказы и Участники — отдельные экраны из меню шапки.
 */
public class CampaignPagerAdapter extends FragmentStateAdapter {

    public static final int TAB_COUNT = 4;

    public CampaignPagerAdapter(@NonNull FragmentActivity activity) {
        super(activity);
    }

    @NonNull
    @Override
    public Fragment createFragment(int position) {
        switch (position) {
            case 1:
                return EntityListFragment.newInstance(EntityType.LOCATION.name());
            case 2:
                return EntityListFragment.newInstance(EntityType.ITEM.name());
            case 3:
                return new MaterialsFragment();
            case 0:
            default:
                return EntityListFragment.newInstance(EntityType.CHARACTERS);
        }
    }

    @Override
    public int getItemCount() {
        return TAB_COUNT;
    }
}
