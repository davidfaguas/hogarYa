import React from 'react';
import { Property } from '../types/property';
import { formatUF } from '../utils/realEstateCalculations';
import { Layers, X, ArrowRight } from 'lucide-react';

interface CompareBarProps {
  comparedProperties: Property[];
  onOpenCompareModal: () => void;
  onRemoveFromCompare: (id: string) => void;
  onClearCompare: () => void;
}

export const CompareBar: React.FC<CompareBarProps> = ({
  comparedProperties,
  onOpenCompareModal,
  onRemoveFromCompare,
  onClearCompare
}) => {
  if (comparedProperties.length === 0) return null;

  return (
    <div className="fixed bottom-4 left-4 right-4 sm:left-auto sm:right-6 sm:max-w-2xl bg-white/98 backdrop-blur-md border border-[#0d5c56]/30 shadow-2xl rounded-2xl p-3 z-40 animate-in slide-in-from-bottom-4 duration-300">
      <div className="flex items-center justify-between gap-3">
        
        {/* Left: Indicator & Thumbnails */}
        <div className="flex items-center gap-3 overflow-hidden">
          <div className="w-10 h-10 rounded-xl bg-[#e8f5f4] text-[#0d5c56] flex items-center justify-center shrink-0">
            <Layers className="w-5 h-5" />
          </div>

          <div className="hidden sm:block">
            <div className="text-xs font-bold text-slate-900">
              Comparar ({comparedProperties.length}/3)
            </div>
            <div className="text-[11px] text-slate-500">
              {comparedProperties.length === 1 
                ? 'Selecciona al menos 1 más' 
                : 'Compara especificaciones lado a lado'}
            </div>
          </div>

          {/* Selected thumbnails */}
          <div className="flex items-center gap-1.5 overflow-x-auto py-0.5">
            {comparedProperties.map((prop) => (
              <div 
                key={prop.id}
                className="relative group shrink-0 w-12 h-12 rounded-lg overflow-hidden border border-slate-200"
              >
                {prop.images.length > 0 ? (
                  <img src={prop.images[0]} alt={prop.title} className="w-full h-full object-cover" />
                ) : (
                  <div className="w-full h-full bg-slate-100 flex items-center justify-center text-[9px] font-bold text-slate-400">
                    Sin foto
                  </div>
                )}
                <button
                  type="button"
                  onClick={() => onRemoveFromCompare(prop.id)}
                  className="absolute inset-0 bg-black/60 text-white flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity"
                  aria-label={`Quitar ${prop.title} de la comparación`}
                >
                  <X className="w-3.5 h-3.5" />
                </button>
              </div>
            ))}
          </div>
        </div>

        {/* Right: Actions */}
        <div className="flex items-center gap-2 shrink-0">
          <button
            type="button"
            onClick={onClearCompare}
            className="text-xs font-semibold text-slate-500 hover:text-slate-800 px-2 py-1.5 transition-colors"
          >
            Limpiar
          </button>

          <button
            type="button"
            onClick={onOpenCompareModal}
            className="flex items-center gap-1.5 py-2 px-3.5 bg-[#0d5c56] hover:bg-[#083d39] text-white text-xs font-bold rounded-xl shadow-md transition-all active:scale-95"
          >
            <span>Comparar ahora</span>
            <ArrowRight className="w-3.5 h-3.5" />
          </button>
        </div>

      </div>
    </div>
  );
};
