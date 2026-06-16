// ========== SUPABASE DATA LAYER ==========
// Hybrid: Supabase → localStorage fallback
// Run after supabase client is initialized (in index.html <head>)

function getSupabase(){ return window.supabaseClient || null; }
const DB = {
  async getUsers() {
    if(getSupabase()) {
      const { data, error } = await getSupabase().from('profiles').select('*').order('created_at');
      if(!error) return data.map(u=>({id:u.id,name:u.name,role:u.role,password:u.password,status:u.status,createdAt:u.created_at,approvedBy:u.approved_by}));
    }
    return JSON.parse(localStorage.getItem('le_users')||'[]');
  },
  async saveUsers(users) {
    localStorage.setItem('le_users', JSON.stringify(users));
    if(getSupabase()) {
      for(const u of users) {
        await getSupabase().from('profiles').upsert({
          id: u.id, name: u.name, role: u.role, password: u.password||'',
          status: u.status, created_at: u.createdAt, approved_by: u.approvedBy||null
        }, {onConflict:'id'});
      }
    }
  },

  // --- Quotes (approval) ---
  async getQuotes() {
    if(getSupabase()) {
      const { data, error } = await getSupabase().from('quotes').select('*').order('submitted_at',{ascending:false});
      if(!error) return data.map(q=>({...q, submittedAt:q.submitted_at, cargoValue:q.cargo_value, leRate:q.le_rate, leDuty:q.le_duty, brokerFee:q.broker_fee, companyName:q.company_name, createdAt:q.created_at}));
    }
    return JSON.parse(localStorage.getItem('le_approval_queue')||'[]');
  },
  async saveQuotes(quotes) {
    localStorage.setItem('le_approval_queue', JSON.stringify(quotes));
    if(getSupabase()) {
      for(const q of quotes) {
        await getSupabase().from('quotes').upsert({
          id: q.id, hs: q.hs, name: q.name, type: q.type, approver: q.approver, note: q.note,
          status: q.status, tag: q.tag||'regular', company_name: q.companyName||'',
          submitted_at: q.submittedAt, mfn: q.mfn, s301: q.s301, s122: q.s122,
          le_rate: q.leRate, le_duty: q.leDuty, broker_fee: q.brokerFee,
          cargo_value: q.cargoValue, total: q.total, client: q.client,
          reject_reason: q.rejectReason, counter_offer: q.counterOffer,
          created_at: q.createdAt||new Date().toISOString()
        }, {onConflict:'id'});
      }
    }
  },

  // --- Direct Requests ---
  async getDirectRequests() {
    if(getSupabase()) {
      const { data, error } = await getSupabase().from('direct_requests').select('*').order('submitted_at',{ascending:false});
      if(!error) return data.map(r=>({...r, submittedAt:r.submitted_at}));
    }
    return JSON.parse(localStorage.getItem('le_direct_requests')||'[]');
  },
  async saveDirectRequests(requests) {
    localStorage.setItem('le_direct_requests', JSON.stringify(requests));
    if(getSupabase()) {
      for(const r of requests) {
        await getSupabase().from('direct_requests').upsert({
          id: r.id, hs: r.hs, name: r.name, client: r.client, exw: r.exw,
          weight: r.weight, note: r.note, status: r.status,
          submitted_at: r.submittedAt, response: r.response
        }, {onConflict:'id'});
      }
    }
  },

  // --- Bulletins ---
  async getBulletins() {
    if(getSupabase()) {
      const { data, error } = await getSupabase().from('bulletins').select('*').order('created_at',{ascending:false}).limit(50);
      if(!error) return data.map(b=>({id:b.id,content:b.content,author:b.author,role:b.role,createdAt:b.created_at}));
    }
    return JSON.parse(localStorage.getItem('le_posts')||'[]');
  },
  async saveBulletins(posts) {
    localStorage.setItem('le_posts', JSON.stringify(posts));
    if(getSupabase()) {
      for(const p of posts) {
        await getSupabase().from('bulletins').upsert({
          id: p.id, content: p.content, author: p.author, role: p.role, created_at: p.createdAt
        }, {onConflict:'id'});
      }
    }
  },

  // --- Comments ---
  async getComments() {
    if(getSupabase()) {
      const { data, error } = await getSupabase().from('comments').select('*').order('created_at',{ascending:true});
      if(!error) {
        const map = {};
        data.forEach(c=>{ if(!map[c.post_id]) map[c.post_id]=[]; map[c.post_id].push({author:c.author,text:c.text,time:c.created_at}); });
        return map;
      }
    }
    return JSON.parse(localStorage.getItem('le_comments')||'{}');
  },
  async addComment(postId, comment) {
    if(getSupabase()) {
      await getSupabase().from('comments').insert({
        post_id: postId, author: comment.author, text: comment.text, created_at: comment.time
      });
    }
    const comments = JSON.parse(localStorage.getItem('le_comments')||'{}');
    if(!comments[postId]) comments[postId]=[];
    comments[postId].push(comment);
    localStorage.setItem('le_comments',JSON.stringify(comments));
  },

  // --- Files ---
  async getFiles() {
    if(getSupabase()) {
      const { data, error } = await getSupabase().from('files').select('*').order('uploaded_at',{ascending:false});
      if(!error) return data.map(f=>({...f, uploadedAt:f.uploaded_at, uploadedBy:f.uploaded_by}));
    }
    return JSON.parse(localStorage.getItem('le_files')||'[]');
  },
  async saveFiles(files) {
    localStorage.setItem('le_files', JSON.stringify(files));
    if(getSupabase()) {
      for(const f of files) {
        await getSupabase().from('files').upsert({
          id: f.id, name: f.name, type: f.type, hs: f.hs, size: f.size,
          data: f.data, uploaded_at: f.uploadedAt, uploaded_by: f.uploadedBy||'anon'
        }, {onConflict:'id'});
      }
    }
  },

  // --- Orders (v2.0+) ---
  async getOrders() {
    if(getSupabase()) {
      const { data, error } = await getSupabase().from('orders').select('*').order('created_at',{ascending:false});
      if(!error) return data.map(o=>({...o, sourceId:o.source_id, confirmedAt:o.confirmed_at, createdAt:o.created_at}));
    }
    return JSON.parse(localStorage.getItem('le_orders')||'[]');
  },
  async saveOrders(orders) {
    localStorage.setItem('le_orders', JSON.stringify(orders));
    if(getSupabase()) {
      for(const o of orders) {
        await getSupabase().from('orders').upsert({
          id: o.id, hs: o.hs, name: o.name, company: o.company||'', client: o.client||'',
          total: o.total||0, status: o.status, logistics: o.logistics||{},
          source_id: o.sourceId, confirmed_at: o.confirmedAt, created_at: o.createdAt
        }, {onConflict:'id'});
      }
    }
  },

  // --- Customer Lost (v1.7+) ---
  async getCustomerLost() {
    if(getSupabase()) {
      const { data, error } = await getSupabase().from('customer_lost').select('*').order('created_at',{ascending:false});
      if(!error) return data.map(c=>({...c, recordedAt:c.recorded_at, createdAt:c.created_at}));
    }
    return JSON.parse(localStorage.getItem('le_customer_lost')||'[]');
  },
  async saveCustomerLost(records) {
    localStorage.setItem('le_customer_lost', JSON.stringify(records));
    if(getSupabase()) {
      for(const r of records) {
        await getSupabase().from('customer_lost').upsert({
          id: r.id, hs: r.hs, name: r.name, company: r.company||'',
          reason: r.reason||'', recorded_at: r.recordedAt, created_at: r.createdAt||new Date().toISOString()
        }, {onConflict:'id'});
      }
    }
  },

  // --- Init: load all from Supabase on startup, refresh in-memory variables ---
  async init() {
    if(!supabase) return;
    try {
      const [users, quotes, directs, bulletins, comments] = await Promise.all([
        DB.getUsers(), DB.getQuotes(), DB.getDirectRequests(), DB.getBulletins(), DB.getComments()
      ]);
      // Also load orders + customer lost (non-blocking)
      const orders = await DB.getOrders().catch(()=>[]);
      const lost = await DB.getCustomerLost().catch(()=>[]);
      // Write to localStorage
      if(users.length) localStorage.setItem('le_users', JSON.stringify(users));
      if(quotes.length) localStorage.setItem('le_approval_queue', JSON.stringify(quotes));
      if(directs.length) localStorage.setItem('le_direct_requests', JSON.stringify(directs));
      if(bulletins.length) localStorage.setItem('le_posts', JSON.stringify(bulletins));
      if(Object.keys(comments).length) localStorage.setItem('le_comments', JSON.stringify(comments));
      if(orders.length) localStorage.setItem('le_orders', JSON.stringify(orders));
      if(lost.length) localStorage.setItem('le_customer_lost', JSON.stringify(lost));
      
      // ** CRITICAL: refresh in-memory arrays so UI shows data immediately **
      if(users.length && typeof registeredUsers !== 'undefined') {
        registeredUsers.splice(0, registeredUsers.length, ...users);
        if(typeof saveUsers === 'function') saveUsers();
      }
      if(quotes.length && typeof approvalQueue !== 'undefined') {
        approvalQueue.splice(0, approvalQueue.length, ...quotes);
        if(typeof saveApprovalQueue === 'function') saveApprovalQueue();
      }
      if(directs.length && typeof directRequests !== 'undefined') {
        directRequests.splice(0, directRequests.length, ...directs);
        if(typeof saveDirectRequests === 'function') saveDirectRequests();
      }
      if(bulletins.length && typeof bulletinPosts !== 'undefined') {
        bulletinPosts.splice(0, bulletinPosts.length, ...bulletins);
      }
      if(Object.keys(comments).length && typeof bulletinComments !== 'undefined') {
        Object.assign(bulletinComments, comments);
      }
      if(orders.length && typeof window.orders !== 'undefined') {
        window.orders.splice(0, window.orders.length, ...orders);
        if(typeof saveOrders === 'function') saveOrders();
      }
      
      // Re-render UI
      if(typeof renderApproval === 'function') renderApproval();
      if(typeof renderBulletins === 'function') renderBulletins();
      if(typeof renderDashboard === 'function') renderDashboard();
      if(typeof renderAdminPanel === 'function') renderAdminPanel();
      
      console.log('✅ Supabase synced: ' + users.length + ' users, ' + quotes.length + ' quotes, ' + orders.length + ' orders');
    } catch(e) {
      console.log('Supabase init: using localStorage only', e.message);
    }
  }
};
