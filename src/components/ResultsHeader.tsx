import React from 'react';
import { 
  SortOption, 
  ViewMode 
} from '../types/property';
import { 
  LayoutGrid, 
  List, 
  Map as MapIcon, 
  SlidersHorizontal,
  Coins,
  ChevronDown,
  Sparkles,
  X
} from 'lucide-react';

interface ResultsHeaderProps {
  totalCount: number;
  filteredCount: number;
  sortOption: SortOption;
  onSortChange: (sort: SortOption) => void;
  viewMode: ViewMode;
  onViewModeChange: (mode: ViewMode) => void;
  priceDisplayMode: 'uf' | 'clp';
  onTogglePriceDisplayMode: () => void;
  onOpenMobileFilters: () => void;
  activeFiltersCount: number;
  comunaName?: string;
  isMobileSimulated?: boolean;
  semanticQuery?: string;
  onClearSemanticQuery?: () => void;
}

export const ResultsHeader: React.FC<ResultsHeaderProps> = ({
  totalCount,
  filteredCount,
  sortOption,
  onSortChange,
  viewMode,
  onViewModeChange,
  priceDisplayMode,
  onTogglePriceDisplayMode,
  onOpenMobileFilters,
  activeFiltersCount,
  comunaName,
  isMobileSimulated = false,
  semanticQuery,
  onClearSemanticQuery
}) => {
  return (
    <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3 pb-3 mb-3 border-b border-slate-200">
      
      {/* Title & Count with Semantic feedback */}
      <div>
        <div className="flex items-center flex-wrap gap-2 text-xs text-slate-500 font-medium">
          <span>
            {filteredCount === totalCount ? '28.513' : filteredCount.toLocaleString('es-CL')} propiedades encontradas{comunaName ? ` en ${comunaName}` : ''}.
          </span>
          {semanticQuery && (
            <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full text-[11px] font-bold bg-teal-50 text-teal-800 border border-teal-200">
              <Sparkles className="w-3 h-3 text-teal-600 shrink-0" />
              <span>Filtro semántico: "{semanticQuery}"</span>
              {onClearSemanticQuery && (
                <button
                  type="button"
                  onClick={onClearSemanticQuery}
                  className="hover:text-red-500 cursor-pointer ml-0.5"
                  title="Quitar filtro semántico"
                >
                  <X className="w-3 h-3" />
                </button>
              )}
            </span>
          )}
        </div>
      </div>

      {/* Controls: Sort Selector & Currency */}
      <div className="flex items-center flex-wrap gap-2.5">
        
        {/* Mobile Filter Button */}
        <button
          type="button"
          onClick={onOpenMobileFilters}
          className={`${isMobileSimulated ? 'flex' : 'lg:hidden flex'} items-center gap-1.5 px-3 py-1.5 bg-white border border-slate-300 rounded-xl text-xs font-semibold text-slate-700 hover:bg-slate-50 shadow-2xs`}
          aria-label="Abrir filtros"
        >
          <SlidersHorizontal className="w-3.5 h-3.5 text-teal-600" />
          <span>Filtros</span>
          {activeFiltersCount > 0 && (
            <span className="w-4 h-4 rounded-full bg-teal-600 text-white text-[10px] font-bold flex items-center justify-center">
              {activeFiltersCount}
            </span>
          )}
        </button>

        {/* Currency Switcher */}
        <button
          onClick={onTogglePriceDisplayMode}
          className="flex items-center gap-1 px-2.5 py-1.5 rounded-xl border border-slate-200 bg-white hover:bg-slate-50 text-xs font-medium text-slate-700 transition-colors shadow-2xs"
          title="Alternar moneda principal"
        >
          <Coins className="w-3.5 h-3.5 text-teal-600" />
          <span className="font-bold text-teal-700">{priceDisplayMode.toUpperCase()}</span>
        </button>

        {/* Sort Select */}
        <div className="relative flex items-center">
          <div className="flex items-center bg-white border border-slate-300 rounded-xl px-3 py-1.5 shadow-2xs hover:border-slate-400 focus-within:ring-2 focus-within:ring-teal-600">
            <span className="text-xs text-slate-500 mr-1.5">Ordenar:</span>
            <select
              id="sort-select"
              value={sortOption}
              onChange={(e) => onSortChange(e.target.value as SortOption)}
              className="bg-transparent text-xs font-bold text-slate-800 focus:outline-none cursor-pointer pr-1"
            >
              <option value="relevancia">Más relevantes</option>
              <option value="menor_precio">Menor precio</option>
              <option value="mayor_precio">Mayor precio</option>
              <option value="mas_recientes">Más recientes</option>
              <option value="menor_precio_m2">Menor precio por m²</option>
            </select>
          </div>
        </div>

      </div>

    </div>
  );
};
