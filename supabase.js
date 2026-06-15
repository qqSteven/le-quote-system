// ========== SUPABASE DATA LAYER ==========
// Hybrid: Supabase → localStorage fallback
// Run after supabase client is initialized (in index.html <head>)

const DB = {
  // --- Users ---
  async getUsers() {
    if(supabase) {
      const { data, error } = await supabase.from('profiles').select('*').order('created_at');
      if(!error) return data.map(u=>({id:u.id,name:u.name,role:u.role,password:u.password,status:u.status,createdAt:u.created_at,approvedBy:u.approved_by}));
    }
    return JSON.parse(localStorage.getItem('le_users')||'[]');
  },
  async saveUsers(users) {
    localStorage.setItem('le_users', JSON.stringify(users));
    if(supabase) {
      for(const u of users) {
        await supabase.from('profiles').upsert({
          id: u.id, name: u.name, role: u.role, password: u.password||'',
          status: u.status, created_at: u.createdAt, approved_by: u.approvedBy||null
        }, {onConflict:'id'});
      }
    }
  },

  // --- Quotes (approval) ---
  async getQuotes() {
    if(supabase) {
      const { data, error } = await supabase.from('quotes').select('*').order('submitted_at',{ascending:false});
      if(!error) return data.map(q=>({...q, submittedAt:q.submitted_at, cargoValue:q.cargo_value, leRate:q.le_rate, leDuty:q.le_duty, brokerFee:q.broker_fee, companyName:q.company_name, createdAt:q.created_at}));
    }
    return JSON.parse(localStorage.getItem('le_approval_queue')||'[]');
  },
  async saveQuotes(quotes) {
    localStorage.setItem('le_approval_queue', JSON.stringify(quotes));
    if(supabase) {
      for(const q of quotes) {
        await supabase.from('quotes').upsert({
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
    if(supabase) {
      const { data, error } = await supabase.from('direct_requests').select('*').order('submitted_at',{ascending:false});
      if(!error) return data.map(r=>({...r, submittedAt:r.submitted_at}));
    }
    return JSON.parse(localStorage.getItem('le_direct_requests')||'[]');
  },
  async saveDirectRequests(requests) {
    localStorage.setItem('le_direct_requests', JSON.stringify(requests));
    if(supabase) {
      for(const r of requests) {
        await supabase.from('direct_requests').upsert({
          id: r.id, hs: r.hs, name: r.name, client: r.client, exw: r.exw,
          weight: r.weight, note: r.note, status: r.status,
          submitted_at: r.submittedAt, response: r.response
        }, {onConflict:'id'});
      }
    }
  },

  // --- Bulletins ---
  async getBulletins() {
    if(supabase) {
      const { data, error } = await supabase.from('bulletins').select('*').order('created_at',{ascending:false}).limit(50);
      if(!error) return data.map(b=>({id:b.id,content:b.content,author:b.author,role:b.role,createdAt:b.created_at}));
    }
    return JSON.parse(localStorage.getItem('le_posts')||'[]');
  },
  async saveBulletins(posts) {
    localStorage.setItem('le_posts', JSON.stringify(posts));
    if(supabase) {
      for(const p of posts) {
        await supabase.from('bulletins').upsert({
          id: p.id, content: p.content, author: p.author, role: p.role, created_at: p.createdAt
        }, {onConflict:'id'});
      }
    }
  },

  // --- Comments ---
  async getComments() {
    if(supabase) {
      const { data, error } = await supabase.from('comments').select('*').order('created_at',{ascending:true});
      if(!error) {
        const map = {};
        data.forEach(c=>{ if(!map[c.post_id]) map[c.post_id]=[]; map[c.post_id].push({author:c.author,text:c.text,time:c.created_at}); });
        return map;
      }
    }
    return JSON.parse(localStorage.getItem('le_comments')||'{}');
  },
  async addComment(postId, comment) {
    if(supabase) {
      await supabase.from('comments').insert({
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
    if(supabase) {
      const { data, error } = await supabase.from('files').select('*').order('uploaded_at',{ascending:false});
      if(!error) return data.map(f=>({...f, uploadedAt:f.uploaded_at, uploadedBy:f.uploaded_by}));
    }
    return JSON.parse(localStorage.getItem('le_files')||'[]');
  },
  async saveFiles(files) {
    localStorage.setItem('le_files', JSON.stringify(files));
    if(supabase) {
      for(const f of files) {
        await supabase.from('files').upsert({
          id: f.id, name: f.name, type: f.type, hs: f.hs, size: f.size,
          data: f.data, uploaded_at: f.uploadedAt, uploaded_by: f.uploadedBy||'anon'
        }, {onConflict:'id'});
      }
    }
  },

  // --- Init: load all from Supabase on startup ---
  async init() {
    if(!supabase) return;
    try {
      // Load all data from Supabase and merge into localStorage for existing code
      const [users, quotes, directs, bulletins, comments] = await Promise.all([
        DB.getUsers(), DB.getQuotes(), DB.getDirectRequests(), DB.getBulletins(), DB.getComments()
      ]);
      if(users.length) localStorage.setItem('le_users', JSON.stringify(users));
      if(quotes.length) localStorage.setItem('le_approval_queue', JSON.stringify(quotes));
      if(directs.length) localStorage.setItem('le_direct_requests', JSON.stringify(directs));
      if(bulletins.length) localStorage.setItem('le_posts', JSON.stringify(bulletins));
      if(Object.keys(comments).length) localStorage.setItem('le_comments', JSON.stringify(comments));
      console.log('✅ Supabase: data loaded into localStorage cache');
    } catch(e) {
      console.log('Supabase init: using localStorage only', e.message);
    }
  }
};
