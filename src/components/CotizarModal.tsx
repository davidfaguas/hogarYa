import React, { useState } from 'react';
import { X, CheckCircle2, Building, ShieldCheck, Mail, Phone, User, Send } from 'lucide-react';
import { Property } from '../types/property';
import { formatUF, formatCLP, ufToClp } from '../utils/realEstateCalculations';

interface CotizarModalProps {
  isOpen: boolean;
  onClose: () => void;
  property: Property | null;
}

export const CotizarModal: React.FC<CotizarModalProps> = ({ isOpen, onClose, property }) => {
  const [nombre, setNombre] = useState('');
  const [rut, setRut] = useState('');
  const [email, setEmail] = useState('');
  const [telefono, setTelefono] = useState('');
  const [preAprobado, setPreAprobado] = useState('si');
  const [mensaje, setMensaje] = useState('Hola, me interesa conocer más información sobre esta casa y coordinar una visita.');
  const [aceptaTerminos, setAceptaTerminos] = useState(true);
  const [enviado, setEnviado] = useState(false);
  const [cargando, setCargando] = useState(false);

  if (!isOpen || !property) return null;

  const priceClp = ufToClp(property.priceUf);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    setCargando(true);
    setTimeout(() => {
      setCargando(false);
      setEnviado(true);
    }, 700);
  };

  const handleClose = () => {
    setEnviado(false);
    onClose();
  };

  return (
    <div 
      className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-black/60 backdrop-blur-xs animate-in fade-in duration-200"
      onClick={handleClose}
      role="dialog"
      aria-modal="true"
      aria-labelledby="cotizar-modal-title"
    >
      <div 
        className="relative w-full max-w-lg bg-white rounded-2xl shadow-2xl overflow-hidden border border-slate-100 animate-in zoom-in-95 duration-200 max-h-[92vh] flex flex-col"
        onClick={(e) => e.stopPropagation()}
      >
        {/* Modal Header HogarYa Style */}
        <div className="bg-[#0f172a] text-white px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-2.5">
            <div className="w-8 h-8 rounded-lg bg-[#0d9488] text-white flex items-center justify-center font-bold text-xs">
              HY
            </div>
            <div>
              <h3 id="cotizar-modal-title" className="text-base font-bold text-white leading-tight">
                Cotizar Propiedad
              </h3>
              <p className="text-xs text-slate-300">
                Directo con {property.seller.name}
              </p>
            </div>
          </div>
          <button 
            type="button"
            onClick={handleClose}
            className="w-8 h-8 rounded-full bg-white/10 hover:bg-white/20 text-white flex items-center justify-center transition-colors focus:outline-none"
            aria-label="Cerrar modal"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Modal Content */}
        <div className="p-6 overflow-y-auto flex-1">
          {enviado ? (
            <div className="text-center py-8 space-y-4">
              <div className="w-16 h-16 rounded-full bg-emerald-100 text-emerald-600 flex items-center justify-center mx-auto animate-bounce">
                <CheckCircle2 className="w-10 h-10" />
              </div>
              <h4 className="text-xl font-bold text-slate-900">
                ¡Cotización enviada con éxito!
              </h4>
              <p className="text-sm text-slate-600 max-w-sm mx-auto">
                Hemos enviado tu solicitud a <strong>{property.seller.name}</strong>. Un ejecutivo especializado te contactará en menos de 24 horas hábiles a <strong>{email || 'tu correo'}</strong> o teléfono móvil.
              </p>
              <div className="p-4 bg-slate-50 rounded-xl border border-slate-200 text-left text-xs text-slate-600 space-y-1.5">
                <p><strong>Propiedad:</strong> {property.title}</p>
                <p><strong>Comuna:</strong> {property.comuna}</p>
                <p><strong>Precio referencial:</strong> {formatUF(property.priceUf)} (~{formatCLP(priceClp)})</p>
                <p><strong>Código de seguimiento:</strong> HY-{Math.floor(100000 + Math.random() * 900000)}</p>
              </div>
              <button
                type="button"
                onClick={handleClose}
                className="w-full py-3 bg-[#0d9488] hover:bg-[#0f766e] text-white font-bold text-sm rounded-xl transition-all shadow-md cursor-pointer"
              >
                Cerrar y seguir explorando
              </button>
            </div>
          ) : (
            <form onSubmit={handleSubmit} className="space-y-4">
              
              {/* Property summary preview */}
              <div className="flex gap-3.5 p-3 bg-slate-50 rounded-xl border border-slate-200 items-center">
                <img 
                  src={property.images[0] || 'https://images.unsplash.com/photo-1600585154340-be6161a56a0c?w=400&q=80'} 
                  alt={property.title}
                  className="w-20 h-16 object-cover rounded-lg shrink-0"
                />
                <div className="min-w-0 flex-1">
                  <span className="text-[10px] font-bold uppercase tracking-wider text-[#0d9488] bg-teal-50 px-2 py-0.5 rounded-full border border-teal-200/60">
                    {property.status === 'nueva' ? 'Nuevo' : 'Usado'} · {property.comuna}
                  </span>
                  <h4 className="text-xs font-bold text-slate-900 truncate mt-1">
                    {property.title}
                  </h4>
                  <div className="text-xs font-extrabold text-slate-900 mt-0.5">
                    {formatUF(property.priceUf)} <span className="text-slate-500 font-normal">/ {formatCLP(priceClp)}</span>
                  </div>
                </div>
              </div>

              {/* Form Fields */}
              <div className="space-y-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Nombre y Apellido *
                  </label>
                  <div className="relative">
                    <User className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
                    <input
                      type="text"
                      required
                      placeholder="Ej: Carolina Morales"
                      value={nombre}
                      onChange={(e) => setNombre(e.target.value)}
                      className="w-full pl-9 pr-3 py-2 text-xs border border-slate-300 rounded-lg focus:ring-2 focus:ring-[#0d9488] focus:border-[#0d9488] outline-none"
                    />
                  </div>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 mb-1">
                      RUT Chileno *
                    </label>
                    <input
                      type="text"
                      required
                      placeholder="Ej: 16.789.012-3"
                      value={rut}
                      onChange={(e) => setRut(e.target.value)}
                      className="w-full px-3 py-2 text-xs border border-slate-300 rounded-lg focus:ring-2 focus:ring-[#0d9488] focus:border-[#0d9488] outline-none"
                    />
                  </div>
                  <div>
                    <label className="block text-xs font-semibold text-slate-700 mb-1">
                      Teléfono móvil *
                    </label>
                    <div className="relative">
                      <Phone className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
                      <input
                        type="tel"
                        required
                        placeholder="+56 9 8765 4321"
                        value={telefono}
                        onChange={(e) => setTelefono(e.target.value)}
                        className="w-full pl-9 pr-3 py-2 text-xs border border-slate-300 rounded-lg focus:ring-2 focus:ring-[#0d9488] focus:border-[#0d9488] outline-none"
                      />
                    </div>
                  </div>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Correo electrónico *
                  </label>
                  <div className="relative">
                    <Mail className="w-4 h-4 text-slate-400 absolute left-3 top-1/2 -translate-y-1/2" />
                    <input
                      type="email"
                      required
                      placeholder="carolina.morales@correo.cl"
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      className="w-full pl-9 pr-3 py-2 text-xs border border-slate-300 rounded-lg focus:ring-2 focus:ring-[#0d9488] focus:border-[#0d9488] outline-none"
                    />
                  </div>
                </div>

                {/* Pre-aprobación hipotecaria */}
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1.5">
                    ¿Tienes tu crédito hipotecario pre-aprobado?
                  </label>
                  <div className="grid grid-cols-3 gap-2">
                    {[
                      { id: 'si', label: 'Sí, aprobado' },
                      { id: 'tramite', label: 'En trámite' },
                      { id: 'asesoria', label: 'Quiero asesoría' }
                    ].map((opt) => (
                      <button
                        key={opt.id}
                        type="button"
                        onClick={() => setPreAprobado(opt.id)}
                        className={`py-2 px-2 text-center rounded-lg text-xs font-semibold border transition-all ${
                          preAprobado === opt.id
                            ? 'bg-teal-50 border-[#0d9488] text-[#0d9488] shadow-2xs font-bold'
                            : 'border-slate-200 text-slate-600 hover:bg-slate-50'
                        }`}
                      >
                        {opt.label}
                      </button>
                    ))}
                  </div>
                </div>

                {/* Mensaje */}
                <div>
                  <label className="block text-xs font-semibold text-slate-700 mb-1">
                    Mensaje o consulta
                  </label>
                  <textarea
                    rows={2}
                    value={mensaje}
                    onChange={(e) => setMensaje(e.target.value)}
                    className="w-full p-2.5 text-xs border border-slate-300 rounded-lg focus:ring-2 focus:ring-[#0d9488] focus:border-[#0d9488] outline-none resize-none text-slate-700"
                  />
                </div>

                {/* Términos */}
                <label className="flex items-start gap-2 cursor-pointer text-[11px] text-slate-600">
                  <input
                    type="checkbox"
                    checked={aceptaTerminos}
                    onChange={(e) => setAceptaTerminos(e.target.checked)}
                    required
                    className="mt-0.5 rounded text-[#0d9488] focus:ring-[#0d9488]"
                  />
                  <span>
                    Acepto ser contactado por la inmobiliaria/corredora y los términos de privacidad de HogarYa Chile.
                  </span>
                </label>
              </div>

              {/* Submit CTA */}
              <button
                type="submit"
                disabled={cargando}
                className="w-full py-3 bg-[#0d9488] hover:bg-[#0f766e] active:scale-[0.99] text-white font-bold text-sm rounded-xl transition-all shadow-md flex items-center justify-center gap-2 cursor-pointer"
              >
                {cargando ? (
                  <div className="w-5 h-5 border-2 border-white border-t-transparent rounded-full animate-spin" />
                ) : (
                  <>
                    <Send className="w-4 h-4" />
                    <span>Enviar Cotización</span>
                  </>
                )}
              </button>
            </form>
          )}
        </div>
      </div>
    </div>
  );
};
