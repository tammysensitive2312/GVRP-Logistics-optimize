/**
 * Navbar Component
 * Manages UI interactions for the application's main navigation bar,
 * including dropdowns and navigation button states.
 */

import { AppState } from '../../core/state.js';
import { Router } from "../../core/router.js";
// import {Sidebar} from "./sidebar.js";

export class Navbar {


    static init() {
        if (typeof window !== 'undefined') {
            window.Navbar = this;
        }
        document.addEventListener('click', this.#closeGlobalDropdown);
    }

    /**
     * On/Off show Dropdown Menu.
     * @param {string} parentId ID của phần tử chứa dropdown (ví dụ: 'userMenuDropdown')
     */
    static toggleDropdown(parentId) {
        const parentElement = document.getElementById(parentId);
        if (!parentElement) return;

        const content = parentElement.querySelector('.dropdown-content');
        if (!content) return;

        document.querySelectorAll('.dropdown-content.show').forEach(openDropdown => {
            if (openDropdown !== content) {
                openDropdown.classList.remove('show');
            }
        });

        content.classList.toggle('show');
    }


    static handleLogout() {
        // alert('Performing logout action...');
        AppState.resetAll();
        Router.goTo(Router.SCREENS.LOGIN);
    }

    /**
     * Handler private to close dropdown when click outside.
     * @private
     */
    static #closeGlobalDropdown = (event) => {
        const openContent = document.querySelector('.user-menu .dropdown-content.show');
        if (!openContent) return;

        const parentMenu = openContent.closest('.user-menu');
        if (parentMenu && !parentMenu.contains(event.target)) {
            openContent.classList.remove('show');
        }
    }

}

window.toggleDropdown = () => Navbar.toggleDropdown();