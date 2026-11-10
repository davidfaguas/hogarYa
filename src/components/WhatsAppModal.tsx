import React from 'react';
import { Property } from '../types/property';
import { formatUF } from '../utils/realEstateCalculations';
import { X, MessageCircle, ExternalLink, ShieldCheck } from 'lucide-react';

interface WhatsAppModalProps {
  isOpen: boolean;
  onClose: () => void;
  property: Property | null;
}

export const WhatsAppModal: React.FC<WhatsAppModalProps> = ({
  isOpen,
  onClose,
  property
}) => {
  if (!isOpen || !property) return null;

  const defaultMsg = `Hola! Vi en Andes Habitat la casa en ${property.comuna} (${property.title}) por ${formatUF(property.priceUf)}. ¿Sigue disponible para agendar visita?`;
  const waUrl = `https://wa.me/${property.seller.whatsapp}?text=${encodeURIComponent(defaultMsg)}`;

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
        <div className="p-4 border-b border-slate-200 flex items-center justify-between bg-emerald-600 text-white">
          <div className="flex items-center gap-2">
            <div className="w-8 h-8 rounded-full bg-white/20 flex items-center justify-center">
              <MessageCircle className="w-5 h-5 fill-white" />
            </div>
            <div>
              <h3 className="text-sm font-bold">Contactar por WhatsApp</h3>
              <p className="text-[11px] text-emerald-100">{property.seller.name}</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="p-1.5 text-white/80 hover:text-white rounded-full hover:bg-white/10"
            aria-label="Cerrar modal de WhatsApp"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content */}
        <div className="p-5 space-y-4 text-xs text-slate-700">
          <div className="p-3 bg-emerald-50 rounded-2xl border border-emerald-100 flex items-center gap-3">
            <div className="w-10 h-10 rounded-full bg-emerald-600 text-white font-bold flex items-center justify-center text-sm shrink-0">
              {property.seller.name.charAt(0)}
            </div>
            <div>
              <p className="font-bold text-slate-900">{property.seller.name}</p>
              <p className="text-[11px] text-slate-500">Número oficial: {property.seller.phone}</p>
            </div>
          </div>

          <div>
            <label className="block font-semibold text-slate-800 mb-1.5">
              Mensaje preparado para enviar:
            </label>
            <div className="p-3 bg-slate-100 rounded-xl text-slate-800 border border-slate-200 italic leading-relaxed text-[11px]">
              "{defaultMsg}"
            </div>
          </div>

          <div className="flex items-center gap-2 text-[11px] text-slate-500">
            <ShieldCheck className="w-4 h-4 text-emerald-600 shrink-0" />
            <span>Contacto verificado a través de la plataforma oficial Andes Habitat.</span>
          </div>

          <div className="pt-2 flex items-center gap-2">
            <button
              type="button"
              onClick={onClose}
              className="flex-1 py-2.5 border border-slate-300 rounded-xl font-semibold text-slate-700 hover:bg-slate-50 text-center"
            >
              Cancelar
            </button>
            <a
              href={waUrl}
              target="_blank"
              rel="noopener noreferrer"
              onClick={onClose}
              className="flex-1 py-2.5 bg-emerald-600 hover:bg-emerald-700 text-white font-bold rounded-xl shadow-md shadow-emerald-600/20 text-center flex items-center justify-center gap-1.5"
            >
              <ExternalLink className="w-3.5 h-3.5" />
              <span>Abrir WhatsApp</span>
            </a>
          </div>

        </div>

      </div>
    </div>
  );
};
