/**
 * ╔══════════════════════════════════════════════════════════════════════════╗
 * ║                         KORBEN COMPONENT v1.0                            ║
 * ║              A sexy, reusable custom element for credit tags             ║
 * ╚══════════════════════════════════════════════════════════════════════════╝
 * 
 * QUICK START:
 * ------------
 * 1. Include this script in your HTML:
 *    <script src="korben.js"></script>
 * 
 * 2. Add <korben> tags anywhere in your HTML:
 *    <korben></korben>
 * 
 * 3. That's it! The component auto-initializes on page load.
 * 
 * 
 * BASIC USAGE:
 * ------------
 * <!-- Default: "Made with ♥ by Korben // 2025" -->
 * <korben></korben>
 * 
 * <!-- In footer -->
 * <footer>
 *   <korben></korben>
 * </footer>
 * 
 * <!-- Multiple instances work fine -->
 * <korben></korben>
 * <korben></korben>
 * 
 * 
 * CUSTOMIZATION VIA DATA ATTRIBUTES:
 * -----------------------------------
 * <!-- Custom author -->
 * <korben data-author="YourName"></korben>
 * 
 * <!-- Custom text before icon -->
 * <korben data-text="Built with"></korben>
 * 
 * <!-- Custom year -->
 * <korben data-year="2024"></korben>
 * 
 * <!-- Custom FontAwesome icon (requires FA loaded) -->
 * <korben data-icon="fas fa-code"></korben>
 * <korben data-icon="fas fa-coffee"></korben>
 * <korben data-icon="fas fa-rocket"></korben>
 * 
 * <!-- Combine multiple attributes -->
 * <korben 
 *   data-text="Crafted with" 
 *   data-icon="fas fa-magic" 
 *   data-author="Alice"
 *   data-year="2025">
 * </korben>
 * 
 * TROUBLESHOOTING:
 * ----------------
 * Q: Icons not showing?
 * A: FontAwesome loads from CDN. Check internet connection or add FA locally.
 * 
 * Q: Multiple tags look the same despite different data attributes?
 * A: Make sure you're calling Korben.init() AFTER the DOM is loaded.
 * 
 * Q: Want to disable auto-init?
 * A: Comment out the last line: // Korben.autoInit();
 *    Then manually call Korben.init() when you want.
 * 
 * 
 * @author Korben // 2025
 * @version 1.0
 * @license MIT
 */

const Korben = (() => {
  // Private: Inject styles once
  const injectStyles = () => {
    if (document.getElementById('korben-styles')) return;

    const styleSheet = document.createElement('style');
    styleSheet.id = 'korben-styles';
    styleSheet.textContent = `
      @import url('https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css');

      korben {
        display: block;
        text-align: center;
        opacity: 0.7;
        cursor: default;
        transition: opacity 0.3s ease-in-out;
        position: relative;
      }

      korben:hover {
        opacity: 1;
      }

      korben code {
        color: #fff;
        font-family: "Space Mono", monospace;
        font-size: .7rem;
        font-weight: 400;
        position: relative;
        z-index: 1;
        overflow: hidden;
        background: rgba(200, 160, 255, 0.2);
        padding: 2px 8px;
        border: 1px solid #8a7fca;
        border-left: 4px solid #8a7fca;
        border-radius: 3px;
        display: inline-block;
        transition: background 0.3s ease-in-out, filter 0.3s ease-in-out;
        text-shadow:
          1px 1px 0 rgba(0, 0, 0, 0.8),
          -1px 1px 0 rgba(0, 0, 0, 0.8),
          1px -1px 0 rgba(0, 0, 0, 0.8),
          -1px -1px 0 rgba(0, 0, 0, 0.8);
      }

      korben code:hover {
        background: rgba(200, 160, 255, 0.3);
        filter: drop-shadow(0 0 8px hotpink);
      }

      korben code::before {
        content: "";
        position: absolute;
        top: -6px;
        left: -6px;
        right: -6px;
        bottom: -6px;
        background: linear-gradient(45deg,
          #ff0066,
          #00ffcc,
          #6600ff,
          #ff0066);
        background-size: 200% 400%;
        filter: blur(8px);
        opacity: 0.5;
        pointer-events: none;
        z-index: 0;
        animation: chroma-shift 5s ease-in-out infinite;
      }

      korben code::after {
        content: "";
        position: absolute;
        top: 0;
        left: 0;
        width: 100%;
        height: 100%;
        background: linear-gradient(45deg,
          #ff0066,
          #00ffcc,
          #6600ff,
          #ff0066);
        background-size: 300% 300%;
        mix-blend-mode: screen;
        opacity: 0.5;
        pointer-events: none;
        z-index: 2;
        animation: chroma-shift 5s ease-in-out infinite;
      }

      korben code .heart {
        color: hotpink;
        filter: drop-shadow(0 0 0px lightgray);
        font-size: 1em;
        vertical-align: middle;
        transition: transform 0.3s ease-in-out, filter 0.3s ease-in-out;
      }

      korben code .heart:hover {
        transform: scale(1.2) rotate(-10deg);
        filter: drop-shadow(0 0 8px black);
      }

      @keyframes chroma-shift {
        0% { background-position: 0% 0%; }
        50% { background-position: 100% 100%; }
        100% { background-position: 0% 0%; }
      }
    `;
    document.head.appendChild(styleSheet);
  };

  // Private: Initialize a single korben element
  const initElement = (el, options = {}) => {
    const {
      text = 'Made with',
      author = 'Korben',
      year = new Date().getFullYear(),
      icon = 'fas fa-heart'
    } = options;

    el.innerHTML = `
      <code>${text} <i class="${icon} heart"></i> by ${author} // ${year}</code>
    `;
  };

  // Public API
  return {
    /**
     * Initialize all <korben> tags on the page
     * @param {Object} options - Configuration options
     * @param {string} options.text - Text before the icon (default: "Made with")
     * @param {string} options.author - Author name (default: "Korben")
     * @param {number} options.year - Year (default: current year)
     * @param {string} options.icon - FontAwesome icon classes (default: "fas fa-heart")
     */
    init: (options = {}) => {
      injectStyles();
      const elements = document.querySelectorAll('korben');
      elements.forEach(el => {
        // Check if element has data attributes that override defaults
        const elementOptions = {
          text: el.dataset.text || options.text,
          author: el.dataset.author || options.author,
          year: el.dataset.year || options.year,
          icon: el.dataset.icon || options.icon
        };
        initElement(el, elementOptions);
      });
    },

    /**
     * Auto-initialize on DOM ready
     */
    autoInit: () => {
      if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', () => Korben.init());
      } else {
        Korben.init();
      }
    }
  };
})();

// Auto-initialize by default
Korben.autoInit();