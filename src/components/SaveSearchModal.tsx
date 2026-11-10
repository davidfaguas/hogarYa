import React, { useState } from 'react';
import { PropertyFilters } from '../types/property';
import { X, BellRing, CheckCircle, Mail } from 'lucide-react';

interface SaveSearchModalProps {
  isOpen: boolean;
  onClose: () => void;
  filters: PropertyFilters;
  searchQuery: string;
}

export const SaveSearchModal: React.FC<SaveSearchModalProps> = ({
  isOpen,
  onClose,
  filters,
  searchQuery
}) => {
  const [email, setEmail] = useState('');
  const [frequency, setFrequency] = useState<'instant' | 'daily'>('daily');
  const [saved, setSaved] = useState(false);

  if (!isOpen) return null;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setSaved(true);
    setTimeout(() => {
      setSaved(false);
      onClose();
    }, 2000);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* Backdrop */}
      <div 
        className="fixed inset-0 bg-slate-900/60 backdrop-blur-xs transition-opacity"
        onClick={onClose}
      />

      {/* Modal Dialog */}
      <div className="relative w-full max-w-md bg-white rounded-3xl shadow-2xl overflow-hidden z-10 animate-in zoom-in-95 duration-200">
        
        {/* Header */}
        <div className="p-4 border-b border-slate-200 flex items-center justify-between bg-[#e8f5f4]">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-full bg-[#0d5c56] text-white flex items-center justify-center">
              <BellRing className="w-4 h-4" />
            </div>
            <div>
              <h3 className="text-sm font-bold text-slate-900">Guardar búsqueda</h3>
              <p className="text-[11px] text-slate-500">Recibe alertas de nuevas casas en venta</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 text-slate-400 hover:text-slate-600 rounded-full hover:bg-slate-200"
            aria-label="Cerrar modal de alerta"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {saved ? (
          <div className="p-8 text-center space-y-2">
            <div className="w-12 h-12 bg-emerald-100 text-emerald-600 rounded-full flex items-center justify-center mx-auto">
              <CheckCircle className="w-6 h-6" />
            </div>
            <h4 className="text-base font-bold text-slate-900">¡Alerta creada con éxito!</h4>
            <p className="text-xs text-slate-500">
              Te notificaremos a <strong>{email}</strong> tan pronto se publique una casa con estos criterios.
            </p>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="p-5 space-y-4 text-xs text-slate-700">
            <p className="text-slate-600 leading-relaxed">
              Configura un aviso para recibir en tu correo las nuevas casas en venta
              {filters.comuna ? ` en ${filters.comuna}` : ''}
              {filters.priceMaxUf ? ` hasta ${filters.priceMaxUf} UF` : ''}.
            </p>

            <div>
              <label className="block font-semibold text-slate-800 mb-1">
                Tu correo electrónico *
              </label>
              <div className="relative">
                <Mail className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
                <input
                  type="email"
                  required
                  placeholder="ejemplo@correo.cl"
                  value={email}
                  onChange={(e) => setEmail(e.target.value)}
                  className="w-full pl-9 pr-3 py-2 border border-slate-300 rounded-xl text-xs focus:ring-2 focus:ring-[#0d5c56]"
                />
              </div>
            </div>

            <div>
              <label className="block font-semibold text-slate-800 mb-1.5">
                Frecuencia del reporte
              </label>
              <div className="grid grid-cols-2 gap-2">
                <button
                  type="button"
                  onClick={() => setFrequency('instant')}
                  className={`py-2 px-3 rounded-xl border text-center font-medium ${
                    frequency === 'instant'
                      ? 'border-[#0d5c56] bg-[#e8f5f4] text-[#0d5c56] font-bold'
                      : 'border-slate-200 text-slate-600'
                  }`}
                >
                  Instantánea (al instante)
                </button>
                <button
                  type="button"
                  onClick={() => setFrequency('daily')}
                  className={`py-2 px-3 rounded-xl border text-center font-medium ${
                    frequency === 'daily'
                      ? 'border-[#0d5c56] bg-[#e8f5f4] text-[#0d5c56] font-bold'
                      : 'border-slate-200 text-slate-600'
                  }`}
                >
                  Resumen Diario
                </button>
              </div>
            </div>

            <div className="pt-2 flex items-center justify-end gap-2">
              <button
                type="button"
                onClick={onClose}
                className="px-4 py-2 border border-slate-300 rounded-xl text-slate-700"
              >
                Cancelar
              </button>
              <button
                type="submit"
                className="px-5 py-2 bg-[#0d5c56] hover:bg-[#083d39] text-white font-bold rounded-xl shadow-md"
              >
                Activar Alerta
              </button>
            </div>

          </form>
        )}

      </div>
    </div>
  );
};
