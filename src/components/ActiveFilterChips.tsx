import React from 'react';
import { X, RotateCcw } from 'lucide-react';
import { PropertyFilters } from '../types/property';
import { formatUF } from '../utils/realEstateCalculations';

interface ActiveFilterChipsProps {
  filters: PropertyFilters;
  searchQuery: string;
  onRemoveFilter: (key: keyof PropertyFilters, value: any) => void;
  onClearSearchQuery: () => void;
  onResetAll: () => void;
}

export const ActiveFilterChips: React.FC<ActiveFilterChipsProps> = ({
  filters,
  searchQuery,
  onRemoveFilter,
  onClearSearchQuery,
  onResetAll
}) => {
  const chips: { id: string; label: string; onRemove: () => void }[] = [];

  if (searchQuery.trim()) {
    chips.push({
      id: 'search',
      label: `Búsqueda: "${searchQuery}"`,
      onRemove: onClearSearchQuery
    });
  }

  if (filters.propertyType && filters.propertyType !== 'todos') {
    chips.push({
      id: 'propertyType',
      label: `Tipo: ${filters.propertyType.charAt(0).toUpperCase() + filters.propertyType.slice(1)}`,
      onRemove: () => onRemoveFilter('propertyType', 'todos')
    });
  }

  if (filters.estado && filters.estado !== 'todas') {
    chips.push({
      id: 'estado',
      label: `Estado: ${filters.estado === 'nueva' ? 'Nueva' : 'Usada'}`,
      onRemove: () => onRemoveFilter('estado', 'todas')
    });
  }

  if (filters.comuna) {
    chips.push({
      id: 'comuna',
      label: `Comuna: ${filters.comuna}`,
      onRemove: () => onRemoveFilter('comuna', '')
    });
  }

  if (filters.region && filters.region !== 'Todas las regiones') {
    chips.push({
      id: 'region',
      label: `Región: ${filters.region}`,
      onRemove: () => onRemoveFilter('region', '')
    });
  }

  if (filters.dormitorios !== null) {
    chips.push({
      id: 'dormitorios',
      label: `${filters.dormitorios === 0 ? 'Studio' : `${filters.dormitorios}+ Dormitorios`}`,
      onRemove: () => onRemoveFilter('dormitorios', null)
    });
  }

  if (filters.banos !== null) {
    chips.push({
      id: 'banos',
      label: `${filters.banos}+ Baños`,
      onRemove: () => onRemoveFilter('banos', null)
    });
  }

  if (filters.priceMinUf || filters.priceMaxUf) {
    let priceLabel = 'Precio: ';
    if (filters.priceMinUf && filters.priceMaxUf) {
      priceLabel += `${formatUF(filters.priceMinUf)} - ${formatUF(filters.priceMaxUf)}`;
    } else if (filters.priceMinUf) {
      priceLabel += `Desde ${formatUF(filters.priceMinUf)}`;
    } else if (filters.priceMaxUf) {
      priceLabel += `Hasta ${formatUF(filters.priceMaxUf)}`;
    }
    chips.push({
      id: 'price',
      label: priceLabel,
      onRemove: () => {
        onRemoveFilter('priceMinUf', null);
        onRemoveFilter('priceMaxUf', null);
      }
    });
  }

  if (filters.m2Min || filters.m2Max) {
    let m2Label = 'Superficie: ';
    if (filters.m2Min && filters.m2Max) {
      m2Label += `${filters.m2Min} - ${filters.m2Max} m²`;
    } else if (filters.m2Min) {
      m2Label += `Desde ${filters.m2Min} m²`;
    } else if (filters.m2Max) {
      m2Label += `Hasta ${filters.m2Max} m²`;
    }
    chips.push({
      id: 'm2',
      label: m2Label,
      onRemove: () => {
        onRemoveFilter('m2Min', null);
        onRemoveFilter('m2Max', null);
      }
    });
  }

  if (filters.tourVirtualOnly) {
    chips.push({
      id: 'tour',
      label: 'Tour virtual 360°',
      onRemove: () => onRemoveFilter('tourVirtualOnly', false)
    });
  }

  if (filters.videoOnly) {
    chips.push({
      id: 'video',
      label: 'Con Video HD',
      onRemove: () => onRemoveFilter('videoOnly', false)
    });
  }

  if (filters.verifiedOnly) {
    chips.push({
      id: 'verified',
      label: 'Verificada',
      onRemove: () => onRemoveFilter('verifiedOnly', false)
    });
  }

  if (filters.subsidioOnly) {
    chips.push({
      id: 'subsidio',
      label: 'Subsidio DS19 / DS1',
      onRemove: () => onRemoveFilter('subsidioOnly', false)
    });
  }

  if (filters.parkingOnly) {
    chips.push({
      id: 'parking',
      label: 'Con Estacionamiento',
      onRemove: () => onRemoveFilter('parkingOnly', false)
    });
  }

  if (chips.length === 0) return null;

  return (
    <div className="flex flex-wrap items-center gap-2 py-2 mb-4 animate-in fade-in">
      <span className="text-xs font-semibold text-slate-500 mr-1">Filtros aplicados:</span>
      {chips.map((chip) => (
        <span
          key={chip.id}
          className="inline-flex items-center gap-1.5 px-3 py-1 bg-white border border-[#0d5c56]/30 text-[#0d5c56] rounded-full text-xs font-semibold shadow-2xs group hover:bg-[#e8f5f4] transition-colors"
        >
          <span>{chip.label}</span>
          <button
            type="button"
            onClick={chip.onRemove}
            className="text-[#0d5c56]/70 hover:text-[#e35238] focus:outline-none"
            aria-label={`Eliminar filtro ${chip.label}`}
          >
            <X className="w-3.5 h-3.5" />
          </button>
        </span>
      ))}
      <button
        type="button"
        onClick={onResetAll}
        className="inline-flex items-center gap-1 text-xs font-semibold text-slate-500 hover:text-[#e35238] transition-colors ml-2 py-1"
      >
        <RotateCcw className="w-3 h-3" />
        <span>Limpiar todos</span>
      </button>
    </div>
  );
};
