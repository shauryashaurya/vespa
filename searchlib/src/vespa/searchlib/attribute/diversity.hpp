// Copyright 2017 Yahoo Holdings. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

#pragma once

#include "diversity.h"

namespace search::attribute::diversity {

template <typename ITR>
ForwardRange<ITR>::ForwardRange(const ForwardRange &) = default;

template <typename ITR>
ForwardRange<ITR>::ForwardRange(const ITR &lower, const ITR &upper)
    : _lower(lower),
      _upper(upper)
{}

template <typename ITR>
ForwardRange<ITR>::~ForwardRange() = default;

template <typename ITR>
ReverseRange<ITR>::ReverseRange(const ReverseRange &) = default;

template <typename ITR>
ReverseRange<ITR>::ReverseRange(const ITR &lower, const ITR &upper)
    : _lower(lower),
      _upper(upper)
{}


template <typename ITR>
ReverseRange<ITR>::~ReverseRange() = default;

template <typename Fetcher>
bool
DiversityFilterT<Fetcher>::accepted(uint32_t docId) {
    if (_total_count < _max_total) {
        if ((_seen.size() < _cutoff_max_groups) || _cutoff_strict) {
            typename Fetcher::ValueType group = _diversity.get(docId);
            if (_seen.size() < _cutoff_max_groups) {
                return conditional_add(_seen[group]);
            } else {
                auto found = _seen.find(group);
                return (found == _seen.end()) ? add() : conditional_add(found->second);
            }
        } else if ( !_cutoff_strict) {
            return add();
        }
    }
    return false;
}


}
