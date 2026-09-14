/* DogsBay report kit: force-directed SVG graph with zoom, pan and focus. Plain ES2020, no dependencies. Requires the base kit. */
(function () {
  'use strict';

  var NS = 'http://www.w3.org/2000/svg';
  var MIN_ZOOM = 0.08;
  var MAX_ZOOM = 6;
  var instance = 0;

  function svgEl(tag, attrs) {
    var node = document.createElementNS(NS, tag);
    if (attrs) Object.keys(attrs).forEach(function (k) { if (attrs[k] != null) node.setAttribute(k, String(attrs[k])); });
    return node;
  }

  var SHAPES = {
    square: function () { return svgEl('rect', { x: -9, y: -9, width: 18, height: 18, rx: 2 }); },
    circle: function () { return svgEl('circle', { r: 10 }); },
    diamond: function () { return svgEl('path', { d: 'M0,-12 L12,0 L0,12 L-12,0 Z' }); },
    chevron: function () { return svgEl('path', { d: 'M-11,-9 L2,-9 L11,0 L2,9 L-11,9 L-3,0 Z' }); }
  };

  function truncate(text, max) {
    text = String(text == null ? '' : text);
    return text.length > max ? text.slice(0, max - 1) + '…' : text;
  }

  function clamp(v, lo, hi) { return Math.max(lo, Math.min(hi, v)); }

  /**
   * Report.graph(svgEl, { nodes, edges,
   *   nodeStyle(node) -> {shape, cls, label, size, important},
   *   edgeStyle(edge) -> {dash, cls, label},
   *   onSelect(node), label(node) })
   * nodes need an id; edges need from and to. Returns { relayout(), setVisible(nodePred, edgePred), select(id),
   *   selected(), has(id), isVisible(id), fit(), zoomIn(), zoomOut() }.
   *
   * The view fits the graph until the viewer zooms or pans; scrolling zooms around the pointer, dragging the
   * background pans, and selecting a node fades everything it is not connected to.
   */
  function graph(svg, options) {
    var opts = options || {};
    var id = ++instance;
    var nodeStyle = opts.nodeStyle || function () { return {}; };
    var edgeStyle = opts.edgeStyle || function () { return {}; };
    var labelOf = opts.label || function (n) { return n.title || n.id; };

    while (svg.firstChild) svg.removeChild(svg.firstChild);
    svg.classList.add('report-graph');
    svg.setAttribute('role', 'group');
    svg.setAttribute('tabindex', '0');
    if (!svg.hasAttribute('aria-label')) svg.setAttribute('aria-label', 'Relationship graph');

    var defs = svgEl('defs');
    var markerId = 'g-arrow-' + id;
    var marker = svgEl('marker', { id: markerId, viewBox: '0 0 10 10', refX: 10, refY: 5, markerWidth: 4, markerHeight: 4, orient: 'auto-start-reverse' });
    marker.appendChild(svgEl('path', { d: 'M0,0 L10,5 L0,10 Z', class: 'g-arrow' }));
    defs.appendChild(marker);
    svg.appendChild(defs);
    var viewport = svgEl('g', { class: 'g-viewport' });
    var edgeLayer = svgEl('g', { class: 'g-edges' });
    var nodeLayer = svgEl('g', { class: 'g-nodes' });
    viewport.appendChild(edgeLayer);
    viewport.appendChild(nodeLayer);
    svg.appendChild(viewport);

    var nodes = [];
    var byId = new Map();
    (opts.nodes || []).forEach(function (data) {
      if (data == null || data.id == null || byId.has(data.id)) return;
      var style = nodeStyle(data) || {};
      var size = style.size > 0 ? style.size : 1;
      var n = { data: data, x: 0, y: 0, vx: 0, vy: 0, fixed: false, visible: true, degree: 0, size: size };
      var cls = 'g-node' + (style.cls ? ' ' + style.cls : '') + (style.important ? ' g-important' : '');
      var g = svgEl('g', { class: cls, tabindex: 0, role: 'button', 'aria-pressed': 'false' });
      var label = labelOf(data) + (style.label ? ' (' + style.label + ')' : '');
      g.setAttribute('aria-label', label);
      var title = svgEl('title');
      title.textContent = label + (data.id !== labelOf(data) ? ' — ' + data.id : '');
      g.appendChild(title);
      var shape = (SHAPES[style.shape] || SHAPES.circle)();
      shape.setAttribute('class', 'g-shape');
      if (size !== 1) shape.setAttribute('transform', 'scale(' + size + ')');
      g.appendChild(shape);
      var text = svgEl('text', { class: 'g-label', y: (12 * size + 13).toFixed(1) });
      text.textContent = truncate(labelOf(data), 34) + (style.label ? ' [' + style.label + ']' : '');
      g.appendChild(text);
      n.el = g;
      n.shape = shape;
      n.label = text;
      // About half the label's width in layout units (12px text, roughly 6.2px a character).
      n.halfWidth = Math.max(14 * size, text.textContent.length * 3.1);
      nodeLayer.appendChild(g);
      nodes.push(n);
      byId.set(data.id, n);
    });

    var edges = [];
    (opts.edges || []).forEach(function (data) {
      var a = byId.get(data.from), b = byId.get(data.to);
      if (!a || !b || a === b) return;
      var style = edgeStyle(data) || {};
      var line = svgEl('line', { class: 'g-edge' + (style.cls ? ' ' + style.cls : ''), 'marker-end': 'url(#' + markerId + ')' });
      if (style.dash) line.setAttribute('stroke-dasharray', style.dash);
      var t = svgEl('title');
      t.textContent = (data.kind || 'edge') + ': ' + labelOf(a.data) + ' → ' + labelOf(b.data) + (style.label ? ' (' + style.label + ')' : '');
      line.appendChild(t);
      edgeLayer.appendChild(line);
      var labelEl = null;
      if (style.label) {
        labelEl = svgEl('text', { class: 'g-edge-label' });
        labelEl.textContent = style.label;
        edgeLayer.appendChild(labelEl);
      }
      a.degree++; b.degree++;
      edges.push({ data: data, a: a, b: b, el: line, labelEl: labelEl, visible: true });
    });

    var selectedNode = null;
    var frame = 0;
    var placed = false;
    // Well-connected nodes (a map that places every topic) repel harder, so hubs spread apart instead of
    // piling up in the middle with their shared topics.
    nodes.forEach(function (n) { n.weight = 1 + Math.sqrt(n.degree) * 0.3; });

    // ── View: world coordinates mapped to the stage by a translate and a scale ──

    var view = { k: 1, tx: 0, ty: 0 };
    var width = 800, height = 600;
    var userMoved = false;
    var shapeScale = 1;

    function measure() {
      var r = svg.getBoundingClientRect();
      width = Math.max(r.width || 0, 200);
      height = Math.max(r.height || 0, 200);
      svg.setAttribute('viewBox', '0 0 ' + width.toFixed(0) + ' ' + height.toFixed(0));
    }

    function applyView() {
      viewport.setAttribute('transform',
        'translate(' + view.tx.toFixed(1) + ',' + view.ty.toFixed(1) + ') scale(' + view.k.toFixed(4) + ')');
      // Labels, halos and edge labels keep their size on screen at any zoom.
      svg.style.setProperty('--g-label-size', (12 / view.k).toFixed(2) + 'px');
      svg.style.setProperty('--g-edge-label-size', (11 / view.k).toFixed(2) + 'px');
      svg.style.setProperty('--g-halo', (3 / view.k).toFixed(2) + 'px');
      svg.classList.toggle('is-zoomed-out', view.k < 0.3);
      // Shapes never shrink below about their natural size on screen, and labels sit a fixed
      // distance below them, so a fitted overview stays legible.
      shapeScale = Math.max(1, 0.9 / view.k);
      nodes.forEach(function (n) {
        var s = n.size * shapeScale;
        n.shape.setAttribute('transform', s === 1 ? '' : 'scale(' + s.toFixed(3) + ')');
        n.label.setAttribute('y', (12 * s + 13 / view.k).toFixed(1));
      });
    }

    function fit() {
      var vis = nodes.filter(function (n) { return n.visible; });
      if (!vis.length) { view = { k: 1, tx: width / 2, ty: height / 2 }; applyView(); return; }
      var minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
      vis.forEach(function (n) {
        minX = Math.min(minX, n.x); maxX = Math.max(maxX, n.x);
        minY = Math.min(minY, n.y); maxY = Math.max(maxY, n.y);
      });
      // Room for labels, which sit below nodes and run wider than them.
      var w = maxX - minX + 180, h = maxY - minY + 90;
      var k = clamp(Math.min(width / w, height / h), MIN_ZOOM, 1.6);
      view.k = k;
      view.tx = width / 2 - k * (minX + maxX) / 2;
      view.ty = height / 2 - k * (minY + maxY) / 2;
      applyView();
    }

    function zoomAt(factor, sx, sy) {
      var k = clamp(view.k * factor, MIN_ZOOM, MAX_ZOOM);
      var f = k / view.k;
      view.tx = sx - (sx - view.tx) * f;
      view.ty = sy - (sy - view.ty) * f;
      view.k = k;
      userMoved = true;
      applyView();
    }

    function stagePoint(evt) {
      var r = svg.getBoundingClientRect();
      return { x: (evt.clientX - r.left) * (width / (r.width || width)), y: (evt.clientY - r.top) * (height / (r.height || height)) };
    }

    function worldPoint(evt) {
      var p = stagePoint(evt);
      return { x: (p.x - view.tx) / view.k, y: (p.y - view.ty) / view.k };
    }

    svg.addEventListener('wheel', function (evt) {
      evt.preventDefault();
      var p = stagePoint(evt);
      var dy = evt.deltaMode === 1 ? evt.deltaY * 16 : evt.deltaY;
      zoomAt(Math.exp(-dy * 0.0015), p.x, p.y);
    }, { passive: false });

    // Dragging the background pans; a click on the background clears the selection.
    var pan = null;
    svg.addEventListener('pointerdown', function (evt) {
      if (evt.button !== 0 || (evt.target.closest && evt.target.closest('.g-node'))) return;
      pan = { sx: evt.clientX, sy: evt.clientY, tx: view.tx, ty: view.ty, moved: false };
      try { svg.setPointerCapture(evt.pointerId); } catch (e) { /* ignore */ }
    });
    svg.addEventListener('pointermove', function (evt) {
      if (!pan) return;
      var r = svg.getBoundingClientRect();
      var dx = (evt.clientX - pan.sx) * (width / (r.width || width));
      var dy = (evt.clientY - pan.sy) * (height / (r.height || height));
      if (Math.abs(dx) + Math.abs(dy) > 3) pan.moved = true;
      if (!pan.moved) return;
      view.tx = pan.tx + dx;
      view.ty = pan.ty + dy;
      userMoved = true;
      svg.classList.add('is-panning');
      applyView();
    });
    function endPan(evt) {
      if (!pan) return;
      var wasClick = !pan.moved;
      pan = null;
      svg.classList.remove('is-panning');
      try { svg.releasePointerCapture(evt.pointerId); } catch (e) { /* ignore */ }
      if (wasClick) select(null);
    }
    svg.addEventListener('pointerup', endPan);
    svg.addEventListener('pointercancel', function () { pan = null; svg.classList.remove('is-panning'); });

    function zoomKey(evt) {
      if (evt.key === '+' || evt.key === '=') { zoomAt(1.25, width / 2, height / 2); }
      else if (evt.key === '-' || evt.key === '_') { zoomAt(0.8, width / 2, height / 2); }
      else if (evt.key === '0') { userMoved = false; fit(); }
      else if (evt.key === 'Escape') { select(null); }
      else return;
      evt.preventDefault();
    }
    svg.addEventListener('keydown', function (evt) { if (evt.target === svg) zoomKey(evt); });

    // ── Drawing ──

    function draw() {
      nodes.forEach(function (n) {
        if (n.visible) n.el.setAttribute('transform', 'translate(' + n.x.toFixed(1) + ',' + n.y.toFixed(1) + ')');
      });
      edges.forEach(function (e) {
        if (!e.visible) return;
        var dx = e.b.x - e.a.x, dy = e.b.y - e.a.y;
        var d = Math.sqrt(dx * dx + dy * dy) || 1;
        var padA = 13 * e.a.size * shapeScale, padB = 13 * e.b.size * shapeScale;
        e.el.setAttribute('x1', (e.a.x + dx / d * padA).toFixed(1));
        e.el.setAttribute('y1', (e.a.y + dy / d * padA).toFixed(1));
        e.el.setAttribute('x2', (e.b.x - dx / d * padB).toFixed(1));
        e.el.setAttribute('y2', (e.b.y - dy / d * padB).toFixed(1));
        if (e.labelEl) {
          e.labelEl.setAttribute('x', ((e.a.x + e.b.x) / 2).toFixed(1));
          e.labelEl.setAttribute('y', ((e.a.y + e.b.y) / 2 - 4).toFixed(1));
        }
      });
    }

    // ── Focus: the selected node, its neighbours and the edges between them stay bright ──

    function applyFocus() {
      var focus = selectedNode && selectedNode.visible ? selectedNode : null;
      svg.classList.toggle('has-focus', !!focus);
      nodes.forEach(function (n) { n.el.classList.remove('is-near'); });
      edges.forEach(function (e) {
        e.el.classList.remove('is-near');
        if (e.labelEl) e.labelEl.classList.remove('is-near');
      });
      if (!focus) return;
      edges.forEach(function (e) {
        if (!e.visible || (e.a !== focus && e.b !== focus)) return;
        e.el.classList.add('is-near');
        if (e.labelEl) e.labelEl.classList.add('is-near');
        e.a.el.classList.add('is-near');
        e.b.el.classList.add('is-near');
        edgeLayer.appendChild(e.el);
      });
      nodeLayer.appendChild(focus.el);
    }

    function select(nodeId, notify) {
      var n = nodeId == null ? null : byId.get(nodeId) || null;
      if (selectedNode) { selectedNode.el.classList.remove('is-selected'); selectedNode.el.setAttribute('aria-pressed', 'false'); }
      selectedNode = n;
      if (n) { n.el.classList.add('is-selected'); n.el.setAttribute('aria-pressed', 'true'); }
      applyFocus();
      if (notify !== false && opts.onSelect) opts.onSelect(n ? n.data : null);
      return !!n;
    }

    // ── Layout ──

    /** The spring length that spreads this many nodes over the stage. */
    function idealLength(count) {
      return clamp(Math.sqrt((width * height) / Math.max(count, 1)) * 0.4, 50, 220);
    }

    /** One simulation step over visible nodes. Returns the largest displacement. */
    function step(active, activeEdges, temperature, L) {
      var count = active.length;
      var i, j, a, dx, dy, d2, d, f;
      var reach = Math.max(500, L * 4);
      var reach2 = reach * reach;
      for (i = 0; i < count; i++) { active[i].fx = 0; active[i].fy = 0; }
      // Repulsion between nodes within reach, bucketed into cells of that size so each node is compared
      // with its own and neighbouring cells only: the same forces as all pairs, without the quadratic cost.
      function repel(p, q) {
        dx = p.x - q.x; dy = p.y - q.y;
        d2 = dx * dx + dy * dy;
        if (d2 < 0.01) { dx = (Math.random() - 0.5); dy = (Math.random() - 0.5); d2 = 0.5; }
        if (d2 > reach2) return;
        f = (L * L) * p.weight * q.weight / d2;
        p.fx += dx * f; p.fy += dy * f;
        q.fx -= dx * f; q.fy -= dy * f;
        // Labels: two nodes whose label boxes overlap push apart sideways and down, so text stays readable
        // where the graph is dense. The box is the label's width at the natural zoom, and a line of text tall.
        var overlapX = (p.halfWidth + q.halfWidth) - Math.abs(dx);
        var overlapY = 34 - Math.abs(dy);
        if (overlapX > 0 && overlapY > 0) {
          var push = Math.min(overlapX, overlapY) * 0.35;
          var sx = dx === 0 ? (Math.random() - 0.5) : Math.sign(dx);
          var sy = dy === 0 ? (Math.random() - 0.5) : Math.sign(dy);
          if (overlapX < overlapY * 2.5) { p.fx += sx * push; q.fx -= sx * push; }
          else { p.fy += sy * push; q.fy -= sy * push; }
        }
      }
      var grid = new Map();
      for (i = 0; i < count; i++) {
        a = active[i];
        a.cx = Math.floor(a.x / reach); a.cy = Math.floor(a.y / reach);
        var key = a.cx + ',' + a.cy;
        var bucket = grid.get(key);
        if (!bucket) { bucket = []; grid.set(key, bucket); }
        bucket.push(i);
      }
      for (i = 0; i < count; i++) {
        a = active[i];
        for (var gx = a.cx - 1; gx <= a.cx + 1; gx++) {
          for (var gy = a.cy - 1; gy <= a.cy + 1; gy++) {
            var cell = grid.get(gx + ',' + gy);
            if (!cell) continue;
            for (var c = 0; c < cell.length; c++) {
              j = cell[c];
              if (j > i) repel(a, active[j]);
            }
          }
        }
      }
      // Springs pull connected nodes to about one spring length apart.
      activeEdges.forEach(function (e) {
        dx = e.b.x - e.a.x; dy = e.b.y - e.a.y;
        d = Math.sqrt(dx * dx + dy * dy) || 0.1;
        f = (d - L) * 0.05 / d;
        e.a.fx += dx * f; e.a.fy += dy * f;
        e.b.fx -= dx * f; e.b.fy -= dy * f;
      });
      // Gravity is weaker along the stage's longer side, so the graph takes the stage's shape.
      var gxPull = 0.01 * Math.min(1, height / width);
      var gyPull = 0.01 * Math.min(1, width / height);
      var maxMove = 0;
      for (i = 0; i < count; i++) {
        a = active[i];
        a.fx -= a.x * gxPull; a.fy -= a.y * gyPull;
        if (a.fixed) { a.vx = 0; a.vy = 0; continue; }
        a.vx = (a.vx + a.fx) * 0.82; // damping
        a.vy = (a.vy + a.fy) * 0.82;
        var speed = Math.sqrt(a.vx * a.vx + a.vy * a.vy);
        if (speed > temperature) { a.vx = a.vx / speed * temperature; a.vy = a.vy / speed * temperature; speed = temperature; }
        a.x += a.vx; a.y += a.vy;
        if (speed > maxMove) maxMove = speed;
      }
      return maxMove;
    }

    function relayout() {
      if (frame) cancelAnimationFrame(frame);
      measure();
      userMoved = false;
      var active = nodes.filter(function (n) { return n.visible; });
      var activeEdges = edges.filter(function (e) { return e.visible; });
      var n = Math.max(active.length, 1);
      var L = idealLength(n);
      if (!placed) {
        // A sunflower spiral at the scale of the layout, so the first frames are already spread out.
        nodes.forEach(function (node, i) {
          var angle = i * 2.399963;
          var radius = L * 0.5 * Math.sqrt(i + 1);
          node.x = Math.cos(angle) * radius * Math.max(1, width / height);
          node.y = Math.sin(angle) * radius * Math.max(1, height / width);
        });
        placed = true;
      }
      // Repulsion is bucketed, so a step costs roughly n times the nodes near each one. Fewer iterations on
      // big graphs, and a capped number of steps per frame, including with reduced motion.
      var iterations = Math.max(40, Math.min(400, Math.floor(4e6 / n)));
      var perFrame = Math.max(1, Math.min(50, Math.floor(1e6 / n)));
      var done = 0;
      var temperature = L * 0.6;
      var cooling = Math.pow(0.5 / temperature, 1 / iterations);
      var reduced = window.matchMedia && window.matchMedia('(prefers-reduced-motion: reduce)').matches;
      function tick() {
        var batch = reduced ? perFrame * 4 : perFrame;
        var moved = 0;
        for (var s = 0; s < batch && done < iterations; s++, done++) {
          moved = step(active, activeEdges, temperature, L);
          temperature *= cooling;
        }
        if (!userMoved) fit();
        draw();
        if (done < iterations && moved > 0.05) frame = requestAnimationFrame(tick);
        else frame = 0;
      }
      tick();
    }

    // ── Node interaction: drag to move, click or Enter to select ──

    nodes.forEach(function (n) {
      var drag = null;
      n.el.addEventListener('pointerdown', function (evt) {
        if (evt.button !== 0) return;
        var p = worldPoint(evt);
        drag = { dx: n.x - p.x, dy: n.y - p.y, sx: evt.clientX, sy: evt.clientY, moved: false };
        n.fixed = true;
        try { n.el.setPointerCapture(evt.pointerId); } catch (e) { /* ignore */ }
        evt.preventDefault();
        evt.stopPropagation();
      });
      n.el.addEventListener('pointermove', function (evt) {
        if (!drag) return;
        if (Math.abs(evt.clientX - drag.sx) + Math.abs(evt.clientY - drag.sy) > 3) drag.moved = true;
        if (!drag.moved) return;
        var p = worldPoint(evt);
        n.x = p.x + drag.dx; n.y = p.y + drag.dy;
        draw();
      });
      function end(evt) {
        if (!drag) return;
        var wasClick = !drag.moved;
        drag = null;
        n.fixed = false;
        try { n.el.releasePointerCapture(evt.pointerId); } catch (e) { /* ignore */ }
        evt.stopPropagation();
        if (wasClick) { select(n.data.id); n.el.focus({ preventScroll: true }); }
      }
      n.el.addEventListener('pointerup', end);
      n.el.addEventListener('pointercancel', function () { drag = null; n.fixed = false; });
      n.el.addEventListener('keydown', function (evt) {
        if (evt.key === 'Enter' || evt.key === ' ') { evt.preventDefault(); select(n.data.id); }
        else zoomKey(evt);
      });
    });

    function setVisible(nodePred, edgePred) {
      nodes.forEach(function (n) {
        n.visible = !nodePred || !!nodePred(n.data);
        n.el.classList.toggle('is-hidden', !n.visible);
        n.el.setAttribute('tabindex', n.visible ? '0' : '-1');
      });
      edges.forEach(function (e) {
        e.visible = e.a.visible && e.b.visible && (!edgePred || !!edgePred(e.data));
        e.el.classList.toggle('is-hidden', !e.visible);
        if (e.labelEl) e.labelEl.classList.toggle('is-hidden', !e.visible);
      });
      if (selectedNode && !selectedNode.visible) select(null);
      applyFocus();
      if (!userMoved) fit();
      draw();
    }

    if (window.ResizeObserver) {
      new ResizeObserver(function () { measure(); if (!userMoved) fit(); }).observe(svg);
    }

    measure();
    applyView();
    relayout();

    return {
      relayout: relayout,
      setVisible: setVisible,
      select: function (nodeId) {
        var ok = select(nodeId);
        if (ok) { var n = byId.get(nodeId); if (n.visible) n.el.focus({ preventScroll: true }); }
        return ok;
      },
      selected: function () { return selectedNode ? selectedNode.data : null; },
      has: function (nodeId) { return byId.has(nodeId); },
      isVisible: function (nodeId) { var n = byId.get(nodeId); return !!(n && n.visible); },
      fit: function () { userMoved = false; fit(); draw(); },
      zoomIn: function () { zoomAt(1.25, width / 2, height / 2); },
      zoomOut: function () { zoomAt(0.8, width / 2, height / 2); }
    };
  }

  window.Report = window.Report || {};
  window.Report.graph = graph;
})();
