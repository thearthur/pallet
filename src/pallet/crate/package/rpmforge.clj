(ns pallet.crate.package.rpmforge
  "Actions for working with the rpmforge repository"
  (:require
   [pallet.stevedore :as stevedore])
  (:use
   [pallet.action :only [action-fn with-action-options]]
   [pallet.actions :only [exec-checked-script package package-manager]]
   [pallet.actions-impl :only [remote-file-action]]
   [pallet.monad :only [phase-pipeline-no-context]]
   [pallet.monad.state-accessors :only [get-session]]
   [pallet.phase :only [def-crate-fn]]))

;;; TODO remove this and use pipeline-when
(def ^{:private true}
  remote-file* (action-fn remote-file-action :direct))

(def ^{:private true}
  rpmforge-url-pattern
  "http://packages.sw.be/rpmforge-release/rpmforge-release-%s.%s.rf.%s.rpm")

;; this is an aggregate so that it can come before the aggragate package-manager
(def-crate-fn add-rpmforge
  "Add the rpmforge repository"
  [& {:keys [version distro arch]
      :or {version "0.5.2-2" distro "el5" arch "i386"}}]
  (with-action-options {:always-before #{package-manager package}}
    (phase-pipeline-no-context add-rpmforge {}
      [session (get-session)]
      (exec-checked-script
       "Add rpmforge repositories"
       (chain-or
        (if (= "0" @(pipe (rpm -qa) (grep rpmforge) (wc -l)))
          (do
            ~(first
              (remote-file*
               session
               "rpmforge.rpm"
               {:url (format rpmforge-url-pattern version distro arch)}))
            ("rpm" -U --quiet "rpmforge.rpm"))))))))
