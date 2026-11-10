import React, { useState } from 'react';
import { Property } from '../types/property';
import { formatUF } from '../utils/realEstateCalculations';
import { X, Send, Phone, CheckCircle, ShieldCheck } from 'lucide-react';

interface ContactModalProps {
  isOpen: boolean;
  onClose: () => void;
  property: Property | null;
}

export const ContactModal: React.FC<ContactModalProps> = ({
  isOpen,
  onClose,
  property
}) => {
  const [submitted, setSubmitted] = useState(false);
  const [formData, setFormData] = useState({
    nombre: '',
    rut: '',
    email: '',
    telefono: '+56 9 ',
    mensaje: '',
    evaluarCredito: true
  });

  if (!isOpen || !property) return null;

  const defaultMessage = `Hola, me interesa la propiedad "${property.title}" en ${property.comuna} (${formatUF(property.priceUf)}). Quisiera coordinar una visita y conocer antecedentes legales.`;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitted(true);
    setTimeout(() => {
      setSubmitted(false);
      onClose();
    }, 2200);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4">
      {/* Backdrop */}
      <div 
        className="fixed inset-0 bg-slate-900/60 backdrop-blur-xs transition-opacity"
        onClick={onClose}
      />

      {/* Modal Dialog */}
      <div className="relative w-full max-w-lg bg-white rounded-3xl shadow-2xl overflow-hidden z-10 animate-in zoom-in-95 duration-200">
        
        {/* Header */}
        <div className="p-5 border-b border-slate-200 flex items-center justify-between bg-slate-50">
          <div>
            <span className="text-[10px] font-bold uppercase tracking-wider text-[#0d5c56] bg-[#e8f5f4] px-2 py-0.5 rounded-md">
              {property.seller.type === 'corredora' ? 'Corredora de Propiedades' : 'Inmobiliaria'}
            </span>
            <h3 className="text-base font-bold text-slate-900 mt-1">
              Contactar a {property.seller.name}
            </h3>
          </div>
          <button
            onClick={onClose}
            className="p-2 text-slate-400 hover:text-slate-600 rounded-full hover:bg-slate-200/60 transition-colors"
            aria-label="Cerrar modal de contacto"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {submitted ? (
          <div className="p-8 text-center space-y-3">
            <div className="w-14 h-14 bg-emerald-100 text-emerald-600 rounded-full flex items-center justify-center mx-auto">
              <CheckCircle className="w-8 h-8" />
            </div>
            <h4 className="text-lg font-bold text-slate-900">¡Mensaje enviado con éxito!</h4>
            <p className="text-xs text-slate-600 max-w-xs mx-auto">
              Un ejecutivo de <strong>{property.seller.name}</strong> se comunicará contigo vía teléfono o correo para coordinar la visita.
            </p>
          </div>
        ) : (
          <form onSubmit={handleSubmit} className="p-6 space-y-4 text-xs text-slate-700">
            
            {/* Property summary */}
            <div className="flex items-center gap-3 p-3 bg-slate-50 rounded-xl border border-slate-200">
              {property.images.length > 0 && (
                <img src={property.images[0]} alt="" className="w-14 h-14 rounded-lg object-cover shrink-0" />
              )}
              <div className="min-w-0 flex-1">
                <p className="font-bold text-slate-900 truncate">{property.title}</p>
                <p className="text-slate-500 text-[11px]">{property.comuna} • {formatUF(property.priceUf)}</p>
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="block font-semibold text-slate-700 mb-1">Nombre completo *</label>
                <input
                  type="text"
                  required
                  placeholder="Ej: Macarena Valdés"
                  value={formData.nombre}
                  onChange={(e) => setFormData({ ...formData, nombre: e.target.value })}
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl text-xs focus:ring-2 focus:ring-[#0d5c56] focus:border-[#0d5c56]"
                />
              </div>

              <div>
                <label className="block font-semibold text-slate-700 mb-1">RUT (opcional)</label>
                <input
                  type="text"
                  placeholder="12.345.678-9"
                  value={formData.rut}
                  onChange={(e) => setFormData({ ...formData, rut: e.target.value })}
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl text-xs focus:ring-2 focus:ring-[#0d5c56] focus:border-[#0d5c56]"
                />
              </div>
            </div>

            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
              <div>
                <label className="block font-semibold text-slate-700 mb-1">Correo electrónico *</label>
                <input
                  type="email"
                  required
                  placeholder="nombre@ejemplo.cl"
                  value={formData.email}
                  onChange={(e) => setFormData({ ...formData, email: e.target.value })}
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl text-xs focus:ring-2 focus:ring-[#0d5c56] focus:border-[#0d5c56]"
                />
              </div>

              <div>
                <label className="block font-semibold text-slate-700 mb-1">Teléfono móvil *</label>
                <input
                  type="tel"
                  required
                  value={formData.telefono}
                  onChange={(e) => setFormData({ ...formData, telefono: e.target.value })}
                  className="w-full px-3 py-2 border border-slate-300 rounded-xl text-xs focus:ring-2 focus:ring-[#0d5c56] focus:border-[#0d5c56]"
                />
              </div>
            </div>

            <div>
              <label className="block font-semibold text-slate-700 mb-1">Mensaje para el vendedor</label>
              <textarea
                rows={3}
                defaultValue={defaultMessage}
                onChange={(e) => setFormData({ ...formData, mensaje: e.target.value })}
                className="w-full px-3 py-2 border border-slate-300 rounded-xl text-xs focus:ring-2 focus:ring-[#0d5c56] focus:border-[#0d5c56]"
              />
            </div>

            <label className="flex items-center gap-2 cursor-pointer select-none text-slate-600">
              <input
                type="checkbox"
                checked={formData.evaluarCredito}
                onChange={(e) => setFormData({ ...formData, evaluarCredito: e.target.checked })}
                className="w-4 h-4 rounded text-[#0d5c56] border-slate-300 focus:ring-[#0d5c56]"
              />
              <span>Quiero simulación de crédito hipotecario con bancos en convenio</span>
            </label>

            <div className="pt-2 flex items-center justify-end gap-2">
              <button
                type="button"
                onClick={onClose}
                className="px-4 py-2.5 border border-slate-300 rounded-xl font-semibold text-slate-700 hover:bg-slate-50"
              >
                Cancelar
              </button>
              <button
                type="submit"
                className="px-6 py-2.5 bg-[#e35238] hover:bg-[#c84128] text-white font-bold rounded-xl shadow-md flex items-center gap-1.5"
              >
                <Send className="w-3.5 h-3.5" />
                <span>Enviar Solicitud</span>
              </button>
            </div>

          </form>
        )}

      </div>
    </div>
  );
};
