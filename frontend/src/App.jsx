import { useEffect, useMemo, useState } from 'react'
import {
  AlertCircle, ArrowUpRight, Boxes, CheckCircle2, ChevronRight, CircleDollarSign,
  ClipboardList, LayoutDashboard, LogIn, MapPin, Menu, Minus, Package, Plus,
  RefreshCw, Search, ShieldCheck, ShoppingBag, Sparkles, Store, Trash2, UserRound, X
} from 'lucide-react'

const API = import.meta.env.VITE_API_URL || 'http://localhost:8080'
const productImages = [
  'https://images.unsplash.com/photo-1523381210434-271e8be1f52b?auto=format&fit=crop&w=900&q=85',
  'https://images.unsplash.com/photo-1602143407151-7111542de6e8?auto=format&fit=crop&w=900&q=85',
  'https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=900&q=85',
  'https://images.unsplash.com/photo-1553062407-98eeb64c6a62?auto=format&fit=crop&w=900&q=85'
]

async function request(path, options = {}) {
  const response = await fetch(`${API}${path}`, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) }
  })
  const body = await response.json().catch(() => ({}))
  if (!response.ok) throw new Error(body.message || body.error || `Request failed (${response.status})`)
  return body
}

function App() {
  const [view, setView] = useState('storefront')
  const [mobileNav, setMobileNav] = useState(false)
  const [auth, setAuth] = useState(() => JSON.parse(localStorage.getItem('marketgrid-auth') || 'null'))
  const [vendors, setVendors] = useState([])
  const [products, setProducts] = useState([])
  const [orders, setOrders] = useState([])
  const [cart, setCart] = useState([])
  const [cartOpen, setCartOpen] = useState(false)
  const [query, setQuery] = useState('')
  const [activeVendor, setActiveVendor] = useState('all')
  const [modal, setModal] = useState(null)
  const [toast, setToast] = useState(null)
  const [loading, setLoading] = useState(false)
  const [apiReady, setApiReady] = useState(null)

  const headers = auth?.token ? { Authorization: `Bearer ${auth.token}` } : {}
  const money = (value) => `$${Number(value || 0).toFixed(2)}`

  async function refreshCatalog() {
    if (!auth) return
    setLoading(true)
    try {
      const vendorResponse = await request(`/api/vendors?tenantId=${encodeURIComponent(auth.tenantId)}`, { headers })
      const nextVendors = vendorResponse.vendors || []
      const productResponses = await Promise.all(nextVendors.map(vendor => request(`/api/products?vendorId=${vendor.id}`, { headers }).catch(() => ({ products: [] }))))
      setVendors(nextVendors)
      setProducts(productResponses.flatMap((response, vendorIndex) => (response.products || []).map(product => ({ ...product, vendorName: nextVendors[vendorIndex].name }))))
      setApiReady(true)
    } catch (error) {
      setApiReady(false)
      notify(error.message)
    } finally { setLoading(false) }
  }

  async function refreshOrders() {
    if (!auth) return
    try { setOrders(await request(`/api/orders?customerId=${auth.customerId}`, { headers })) } catch (error) { notify(error.message) }
  }

  useEffect(() => { if (auth) { refreshCatalog(); refreshOrders() } }, [auth])
  useEffect(() => { if (!toast) return; const timer = setTimeout(() => setToast(null), 3600); return () => clearTimeout(timer) }, [toast])

  function notify(message, type = 'error') { setToast({ message, type }) }
  function saveAuth(nextAuth) { setAuth(nextAuth); localStorage.setItem('marketgrid-auth', JSON.stringify(nextAuth)); setModal(null) }
  function signOut() { setAuth(null); localStorage.removeItem('marketgrid-auth'); setCart([]); setView('storefront') }
  function addToCart(product) {
    setCart(current => { const found = current.find(item => item.id === product.id); return found ? current.map(item => item.id === product.id ? { ...item, quantity: Math.min(item.quantity + 1, product.stock) } : item) : [...current, { ...product, quantity: 1 }] })
    notify(`${product.name} added to cart`, 'success')
  }
  function updateQuantity(id, change) { setCart(current => current.map(item => item.id === id ? { ...item, quantity: Math.max(0, Math.min(item.quantity + change, item.stock)) } : item).filter(item => item.quantity > 0)) }

  const visibleProducts = useMemo(() => products.filter(product => (activeVendor === 'all' || product.vendorId === activeVendor) && product.name.toLowerCase().includes(query.toLowerCase())), [products, activeVendor, query])
  const cartCount = cart.reduce((total, item) => total + item.quantity, 0)
  const cartTotal = cart.reduce((total, item) => total + Number(item.price) * item.quantity, 0)

  if (!auth) return <AuthScreen onAuth={saveAuth} />

  return <div className="app-shell">
    <header className="topbar">
      <button className="mobile-menu icon-button" onClick={() => setMobileNav(!mobileNav)} aria-label="Toggle navigation"><Menu size={20} /></button>
      <div className="brand-mark"><span>MG</span><div><strong>marketgrid</strong><small>commerce network</small></div></div>
      <div className="topbar-context"><span className="status-dot" /> Tenant / {auth.tenantId}</div>
      <div className="topbar-actions"><button className="icon-button" onClick={() => setView('orders')} aria-label="Orders"><ClipboardList size={19} /></button><button className="icon-button cart-toggle" onClick={() => setCartOpen(current => !current)} aria-label={`${cartOpen ? 'Hide' : 'Show'} basket`} aria-expanded={cartOpen}><ShoppingBag size={19} />{cartCount > 0 && <span className="cart-count">{cartCount}</span>}</button><button className="profile-chip" onClick={() => signOut()}><span>{auth.name?.slice(0, 1) || 'M'}</span><strong>{auth.name || 'Account'}</strong></button></div>
    </header>
    <aside className={`sidebar ${mobileNav ? 'open' : ''}`}>
      <div className="sidebar-label">Workspace</div>
      <NavItem icon={<LayoutDashboard size={18} />} label="Marketplace" active={view === 'storefront'} onClick={() => { setView('storefront'); setMobileNav(false) }} />
      <NavItem icon={<ClipboardList size={18} />} label="Your orders" count={orders.length} active={view === 'orders'} onClick={() => { setView('orders'); setMobileNav(false) }} />
      <NavItem icon={<Store size={18} />} label="Vendor studio" active={view === 'vendor'} onClick={() => { setView('vendor'); setMobileNav(false) }} />
      <div className="sidebar-divider" />
      <div className="tenant-panel"><div className="tenant-icon"><ShieldCheck size={18} /></div><div><span>Connected tenant</span><strong>{auth.tenantId}</strong></div></div>
      <div className="sidebar-foot"><span>MarketGrid v1.0</span><button onClick={refreshCatalog} className="icon-button subtle" aria-label="Refresh data"><RefreshCw size={15} /></button></div>
    </aside>
    <main className="main-content">
      {view === 'storefront' && <Storefront products={visibleProducts} vendors={vendors} query={query} setQuery={setQuery} activeVendor={activeVendor} setActiveVendor={setActiveVendor} addToCart={addToCart} loading={loading} apiReady={apiReady} onSeed={() => setModal('seed')} />}
      {view === 'orders' && <Orders orders={orders} money={money} onRefresh={refreshOrders} />}
      {view === 'vendor' && <VendorStudio vendors={vendors} products={products} auth={auth} headers={headers} onCreated={refreshCatalog} notify={notify} />}
    </main>
    {cartOpen && <CartDrawer cart={cart} total={cartTotal} count={cartCount} money={money} updateQuantity={updateQuantity} onCheckout={() => setModal('checkout')} />}
    {modal === 'checkout' && <CheckoutModal cart={cart} total={cartTotal} money={money} auth={auth} headers={headers} onClose={() => setModal(null)} onComplete={(order) => { setCart([]); setModal(null); setOrders(current => [order, ...current]); setView('orders'); notify('Order confirmed across all selected vendors', 'success') }} onError={notify} />}
    {modal === 'seed' && <SeedModal auth={auth} headers={headers} onClose={() => setModal(null)} onDone={() => { setModal(null); refreshCatalog(); notify('Demo marketplace created', 'success') }} onError={notify} />}
    {toast && <div className={`toast ${toast.type}`}><span>{toast.type === 'success' ? <CheckCircle2 size={17} /> : <AlertCircle size={17} />}</span>{toast.message}</div>}
  </div>
}

function NavItem({ icon, label, count, active, onClick }) { return <button className={`nav-item ${active ? 'active' : ''}`} onClick={onClick}>{icon}<span>{label}</span>{count > 0 && <em>{count}</em>}</button> }

function AuthScreen({ onAuth }) {
  const [mode, setMode] = useState('login')
  const [form, setForm] = useState({ name: '', email: '', password: '', tenantId: 'tenant-a' })
  const [error, setError] = useState('')
  const [busy, setBusy] = useState(false)
  async function submit(event) {
    event.preventDefault(); setBusy(true); setError('')
    try {
      if (mode === 'register') await request('/api/auth/register', { method: 'POST', body: JSON.stringify(form) })
      const result = await request('/api/auth/login', { method: 'POST', body: JSON.stringify({ email: form.email, password: form.password }) })
      onAuth(result)
    } catch (err) { setError(err.message) } finally { setBusy(false) }
  }
  return <div className="auth-layout"><div className="auth-art"><div className="art-kicker"><span className="status-dot" /> MARKETGRID COMMERCE</div><div className="art-copy"><p className="eyebrow">One network. Many makers.</p><h1>Commerce with a <i>human</i> supply chain.</h1><p>Discover independent vendors, build a basket across storefronts, and keep every seller in the loop.</p></div><div className="art-stats"><span><strong>24k+</strong><small>active makers</small></span><span><strong>1.8m</strong><small>orders fulfilled</small></span><span><strong>98.6%</strong><small>on-time delivery</small></span></div></div><div className="auth-panel"><div className="brand-mark dark"><span>MG</span><div><strong>marketgrid</strong><small>commerce network</small></div></div><div className="auth-form"><p className="eyebrow">{mode === 'login' ? 'Welcome back' : 'Create workspace access'}</p><h2>{mode === 'login' ? 'Sign in to your grid' : 'Join the marketplace'}</h2><p className="muted">{mode === 'login' ? 'Manage your buying and selling activity in one place.' : 'Start exploring products from independent vendors.'}</p><form onSubmit={submit}>{mode === 'register' && <label>Full name<input required value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} placeholder="Avery Morgan" /></label>}<label>Email address<input required type="email" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} placeholder="you@example.com" /></label><label>Password<input required minLength="4" type="password" value={form.password} onChange={e => setForm({ ...form, password: e.target.value })} placeholder="Your password" /></label>{mode === 'register' && <label>Tenant ID<input value={form.tenantId} onChange={e => setForm({ ...form, tenantId: e.target.value })} /></label>} {error && <div className="form-error"><AlertCircle size={16} />{error}</div>}<button className="primary-button wide" disabled={busy}>{busy ? 'Connecting...' : mode === 'login' ? <><LogIn size={17} /> Sign in</> : <><UserRound size={17} /> Create account</>}</button></form><button className="text-button" onClick={() => { setMode(mode === 'login' ? 'register' : 'login'); setError('') }}>{mode === 'login' ? 'New to MarketGrid? Create an account' : 'Already have an account? Sign in'}</button></div><div className="auth-foot"><ShieldCheck size={15} /> Secure tenant-aware access</div></div></div>
}

function Storefront({ products, vendors, query, setQuery, activeVendor, setActiveVendor, addToCart, loading, apiReady, onSeed }) {
  return <div className="page"><div className="page-heading"><div><p className="eyebrow">Customer storefront</p><h1>Find your next <i>favorite.</i></h1><p className="muted heading-copy">A living catalog from the vendors that make MarketGrid interesting.</p></div><div className="connection-badge">{apiReady === false ? <><span className="offline-dot" /> API offline</> : <><span className="status-dot" /> {apiReady === true ? 'Live catalog' : 'Ready to explore'}</>}</div></div><section className="feature-band"><div><span className="feature-tag"><Sparkles size={14} /> Curated this week</span><h2>Objects with a point of view.</h2><p>Small-batch goods, considered by the people who made them.</p><button className="light-button" onClick={() => document.getElementById('catalog')?.scrollIntoView({ behavior: 'smooth' })}>Browse the catalog <ArrowUpRight size={16} /></button></div><div className="feature-visual"><img src={productImages[0]} alt="Textile product from a marketplace vendor" /><div className="visual-note">FIELD NOTES<br /><strong>no. 04</strong></div></div></section><div className="catalog-toolbar" id="catalog"><div className="search-box"><Search size={18} /><input value={query} onChange={e => setQuery(e.target.value)} placeholder="Search the marketplace" /></div><div className="vendor-filters"><button className={activeVendor === 'all' ? 'selected' : ''} onClick={() => setActiveVendor('all')}>All vendors</button>{vendors.map(vendor => <button className={activeVendor === vendor.id ? 'selected' : ''} key={vendor.id} onClick={() => setActiveVendor(vendor.id)}>{vendor.name}</button>)}</div></div>{loading ? <div className="empty-state"><RefreshCw className="spin" size={25} /><p>Refreshing the catalog...</p></div> : products.length === 0 ? <div className="empty-state"><Boxes size={32} /><h3>Your marketplace is waiting for its first makers.</h3><p>Seed a demo catalog or create vendors from the Vendor Studio.</p><button className="primary-button" onClick={onSeed}><Plus size={16} /> Seed demo marketplace</button></div> : <div className="product-grid">{products.map((product, index) => <ProductCard key={product.id} product={product} image={productImages[index % productImages.length]} onAdd={() => addToCart(product)} />)}</div>}</div>
}

function ProductCard({ product, image, onAdd }) { return <article className="product-card"><div className="product-image"><img src={product.imageUrl ? `${API}/api/products/${product.id}/image` : image} alt={product.name} /><span className="product-vendor">{product.vendorName}</span><button className="quick-add" onClick={onAdd} aria-label={`Add ${product.name} to cart`}><Plus size={19} /></button></div><div className="product-info"><div><h3>{product.name}</h3><p>{product.stock} available</p></div><strong>${Number(product.price).toFixed(2)}</strong></div></article> }

function CartDrawer({ cart, total, count, money, updateQuantity, onCheckout }) { return <aside className="cart-drawer"><div className="cart-header"><div><p className="eyebrow">Your basket</p><h2>{count} {count === 1 ? 'item' : 'items'}</h2></div><ShoppingBag size={21} /></div>{cart.length === 0 ? <div className="cart-empty"><ShoppingBag size={27} /><p>Your basket is ready for something good.</p></div> : <><div className="cart-items">{cart.map(item => <div className="cart-item" key={item.id}><div className="cart-thumb"><img src={productImages[0]} alt="" /></div><div className="cart-item-copy"><strong>{item.name}</strong><small>{item.vendorName}</small><span>{money(item.price)}</span></div><div className="quantity"><button onClick={() => updateQuantity(item.id, -1)} aria-label="Decrease quantity"><Minus size={13} /></button><span>{item.quantity}</span><button onClick={() => updateQuantity(item.id, 1)} aria-label="Increase quantity"><Plus size={13} /></button></div></div>)}</div><div className="cart-summary"><div><span>Subtotal</span><strong>{money(total)}</strong></div><small>Shipping and taxes calculated at checkout</small><button className="primary-button wide" onClick={onCheckout}>Review checkout <ChevronRight size={17} /></button></div></>}</aside> }

function Orders({ orders, money, onRefresh }) { return <div className="page compact-page"><div className="page-heading row-heading"><div><p className="eyebrow">Customer account</p><h1>Your orders</h1><p className="muted heading-copy">Every order, across every vendor, in one clear timeline.</p></div><button className="outline-button" onClick={onRefresh}><RefreshCw size={16} /> Refresh</button></div>{orders.length === 0 ? <div className="empty-state large"><ClipboardList size={32} /><h3>No orders yet.</h3><p>Once you place a combined order, its full vendor trail will appear here.</p></div> : <div className="orders-list">{orders.map(order => <article className="order-card" key={order.id}><div className="order-card-top"><div><span className="order-number">ORDER / {order.id.slice(0, 8).toUpperCase()}</span><h3>{new Date(order.createdAt).toLocaleDateString(undefined, { month: 'long', day: 'numeric', year: 'numeric' })}</h3></div><span className="order-status"><CheckCircle2 size={15} /> {order.status}</span></div><div className="order-vendors">{[...new Set(order.items.map(item => item.vendorId))].map((vendorId, index) => <span key={vendorId}><span className={`vendor-dot dot-${index % 3}`} /> Vendor {index + 1}</span>)}</div><div className="order-card-bottom"><span>{order.items.reduce((sum, item) => sum + item.quantity, 0)} items from {new Set(order.items.map(item => item.vendorId)).size} vendors</span><strong>{money(order.items.reduce((sum, item) => sum + Number(item.unitPrice || 0) * item.quantity, 0))}</strong></div></article>)}</div>}</div> }

function VendorStudio({ vendors, products, auth, headers, onCreated, notify }) { const [form, setForm] = useState({ name: '', email: '' }); const [productForm, setProductForm] = useState({ vendorId: '', name: '', category: '', description: '', price: '', stock: '' }); const [busy, setBusy] = useState(false); async function createVendor(e) { e.preventDefault(); setBusy(true); try { await request('/api/vendors', { method: 'POST', headers, body: JSON.stringify({ ...form, tenantId: auth.tenantId }) }); setForm({ name: '', email: '' }); onCreated(); notify('Vendor created', 'success') } catch (error) { notify(error.message) } finally { setBusy(false) } } async function createProduct(e) { e.preventDefault(); setBusy(true); try { await request('/api/products', { method: 'POST', headers, body: JSON.stringify({ ...productForm, price: Number(productForm.price), stock: Number(productForm.stock), tenantId: auth.tenantId }) }); setProductForm({ vendorId: productForm.vendorId, name: '', category: '', description: '', price: '', stock: '' }); onCreated(); notify('Product listed', 'success') } catch (error) { notify(error.message) } finally { setBusy(false) } } async function regenerateImage(id) { setBusy(true); try { await request(`/api/products/${id}/regenerate-image`, { method: 'POST', headers }); onCreated(); notify('Product image regenerated', 'success') } catch (error) { notify(error.message) } finally { setBusy(false) } } return <div className="page compact-page"><div className="page-heading"><div><p className="eyebrow">Seller workspace</p><h1>Vendor studio</h1><p className="muted heading-copy">Keep your catalog current and your stock visible.</p></div></div><div className="studio-grid"><section className="studio-panel"><div className="panel-heading"><div className="panel-icon green"><Store size={18} /></div><div><h2>Vendor profiles</h2><p>Manage the sellers in this tenant.</p></div></div><div className="vendor-list">{vendors.map(vendor => <div className="vendor-row" key={vendor.id}><span className="vendor-avatar">{vendor.name.slice(0, 1)}</span><div><strong>{vendor.name}</strong><small>{vendor.email}</small></div><span className="active-label">Active</span></div>)}</div><form className="inline-form" onSubmit={createVendor}><input required value={form.name} onChange={e => setForm({ ...form, name: e.target.value })} placeholder="Vendor name" /><input required type="email" value={form.email} onChange={e => setForm({ ...form, email: e.target.value })} placeholder="Contact email" /><button className="primary-button" disabled={busy}><Plus size={16} /> Add vendor</button></form></section><section className="studio-panel"><div className="panel-heading"><div className="panel-icon orange"><Package size={18} /></div><div><h2>List a product</h2><p>Publish inventory to your storefront.</p></div></div><form className="stack-form" onSubmit={createProduct}><label>Vendor<select required value={productForm.vendorId} onChange={e => setProductForm({ ...productForm, vendorId: e.target.value })}><option value="">Select vendor</option>{vendors.map(vendor => <option key={vendor.id} value={vendor.id}>{vendor.name}</option>)}</select></label><label>Product name<input required value={productForm.name} onChange={e => setProductForm({ ...productForm, name: e.target.value })} placeholder="Product name" /></label><label>Category<input value={productForm.category} onChange={e => setProductForm({ ...productForm, category: e.target.value })} placeholder="Category, e.g. ceramics" /></label><label>Description<textarea value={productForm.description} onChange={e => setProductForm({ ...productForm, description: e.target.value })} placeholder="A short description for the product image" /></label><div className="two-fields"><label>Price<input required type="number" min="0" step="0.01" value={productForm.price} onChange={e => setProductForm({ ...productForm, price: e.target.value })} placeholder="0.00" /></label><label>Stock<input required type="number" min="0" value={productForm.stock} onChange={e => setProductForm({ ...productForm, stock: e.target.value })} placeholder="0" /></label></div><button className="primary-button" disabled={busy}><Plus size={16} /> Publish product</button></form></section></div><section className="inventory-panel"><div className="panel-heading"><div className="panel-icon blue"><Boxes size={18} /></div><div><h2>Inventory overview</h2><p>{products.length} products currently listed</p></div></div><div className="inventory-table"><div className="inventory-row table-header"><span>Product</span><span>Vendor</span><span>Price</span><span>Available</span><span>Image</span></div>{products.map(product => <div className="inventory-row" key={product.id}><strong>{product.name}</strong><span>{product.vendorName}</span><span>${Number(product.price).toFixed(2)}</span><span className={product.stock < 2 ? 'low-stock' : ''}>{product.stock} units</span><button className="text-button" disabled={busy} onClick={() => regenerateImage(product.id)}><Sparkles size={14} /> Regenerate</button></div>)}</div></section></div> }

function CheckoutModal({ cart, total, money, auth, headers, onClose, onComplete, onError }) { const [busy, setBusy] = useState(false); async function placeOrder() { setBusy(true); try { const order = await request('/api/orders', { method: 'POST', headers, body: JSON.stringify({ customerId: auth.customerId, tenantId: auth.tenantId, items: cart.map(item => ({ productId: item.id, vendorId: item.vendorId, quantity: item.quantity, unitPrice: item.price })) }) }); onComplete(order) } catch (error) { onError(error.message) } finally { setBusy(false) } } return <div className="modal-backdrop"><div className="modal"><button className="modal-close" onClick={onClose} aria-label="Close checkout"><X size={19} /></button><p className="eyebrow">Final review</p><h2>Ready to place your order?</h2><p className="muted">Your basket includes products from {new Set(cart.map(item => item.vendorId)).size} independent vendors. Stock will be reserved together.</p><div className="checkout-items">{cart.map(item => <div key={item.id}><span>{item.quantity} x {item.name}</span><strong>{money(item.price * item.quantity)}</strong></div>)}</div><div className="checkout-total"><span>Total</span><strong>{money(total)}</strong></div><div className="secure-note"><ShieldCheck size={16} /> Encrypted checkout via MarketGrid gateway</div><button className="primary-button wide" disabled={busy} onClick={placeOrder}>{busy ? 'Reserving inventory...' : 'Place order'} <ArrowUpRight size={17} /></button></div></div> }

function SeedModal({ auth, headers, onClose, onDone, onError }) { const [busy, setBusy] = useState(false); async function seed() { setBusy(true); try { const names = [['Northwind Atelier', 'northwind@example.com'], ['Cedar & Co.', 'cedar@example.com']]; for (const [name, email] of names) await request('/api/vendors', { method: 'POST', headers, body: JSON.stringify({ tenantId: auth.tenantId, name, email }) }); onDone() } catch (error) { onError(error.message) } finally { setBusy(false) } } return <div className="modal-backdrop"><div className="modal"><button className="modal-close" onClick={onClose} aria-label="Close"><X size={19} /></button><div className="modal-symbol"><Sparkles size={20} /></div><p className="eyebrow">Quick start</p><h2>Set up a sample marketplace?</h2><p className="muted">This creates two demo vendors. Add products to them from Vendor Studio, then try a combined checkout.</p><div className="seed-preview"><span><Store size={16} /> Northwind Atelier</span><span><Store size={16} /> Cedar & Co.</span></div><button className="primary-button wide" disabled={busy} onClick={seed}>{busy ? 'Creating vendors...' : 'Create demo vendors'}</button></div></div> }

export default App
