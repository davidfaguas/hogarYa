import React from 'react';
import { PropertyFilters } from '../types/property';
import { FilterControls } from './FilterControls';

interface FilterSidebarProps {
  filters: PropertyFilters;
  onFilterChange: (updated: Partial<PropertyFilters>) => void;
  onResetFilters: () => void;
  onOpenSaveAlertModal: () => void;
  filteredCount: number;
}

export const FilterSidebar: React.FC<FilterSidebarProps> = ({
  filters,
  onFilterChange,
  onResetFilters,
  onOpenSaveAlertModal,
  filteredCount
}) => {
  return (
    <aside className="w-[280px] shrink-0 bg-white rounded-2xl border border-slate-200/90 p-5 shadow-xs h-fit sticky top-36">
      <FilterControls
        filters={filters}
        onFilterChange={onFilterChange}
        onResetFilters={onResetFilters}
        onOpenSaveAlertModal={onOpenSaveAlertModal}
      />
    </aside>
  );
};
