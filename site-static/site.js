// Small enhancements; every page works without JavaScript.

// Track filter: <input data-filter=".track-card"> hides cards whose data-filter-text doesn't match.
document.querySelectorAll("input[data-filter]").forEach((input) => {
  const items = Array.from(document.querySelectorAll(input.dataset.filter));
  const empty = document.querySelector(".empty-filter");
  input.addEventListener("input", () => {
    const q = input.value.trim().toLowerCase();
    let shown = 0;
    items.forEach((el) => {
      const match = !q || (el.dataset.filterText || el.textContent).toLowerCase().includes(q);
      el.classList.toggle("hidden", !match);
      if (match) shown++;
    });
    // Hide track-type headings and series whose cards are all filtered out.
    document.querySelectorAll(".track-grid").forEach((grid) => {
      const any = grid.querySelector(".track-card:not(.hidden)");
      grid.classList.toggle("hidden", !any);
      const heading = grid.previousElementSibling;
      if (heading && heading.matches(".track-type")) heading.classList.toggle("hidden", !any);
    });
    document.querySelectorAll("section.series").forEach((sec) => {
      sec.classList.toggle("hidden", !sec.querySelector(".track-card:not(.hidden)"));
    });
    if (empty) empty.classList.toggle("hidden", shown > 0);
  });
});

// Copy buttons: <button data-copy="text">.
document.querySelectorAll("button[data-copy]").forEach((btn) => {
  btn.addEventListener("click", async () => {
    const label = btn.textContent;
    try {
      await navigator.clipboard.writeText(btn.dataset.copy);
      btn.textContent = "Copied!";
    } catch (e) {
      btn.textContent = "Couldn't copy";
    }
    setTimeout(() => (btn.textContent = label), 1500);
  });
});
