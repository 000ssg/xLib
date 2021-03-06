/*
 * The MIT License
 *
 * Copyright 2020 Sergey Sidorov/000ssg@gmail.com
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
package ssg.lib.di.base;

import ssg.lib.di.DF;

/**
 * Implement support of nested filters for a filter an
 *
 * @author 000ssg
 */
public abstract class BaseDF<T, P> implements DF<T, P> {

    DF<T, P> filter;

    public <D extends DF<T, P>> D configure(DF<T, P> filter) {
        filter(filter);
        return (D) this;
    }

    @Override
    public void filter(DF<T, P> filter) {
        this.filter = filter;
    }

    @Override
    public DF<T, P> filter() {
        return filter;
    }

    @Override
    public String toString(P provider) {
        StringBuilder sb=new StringBuilder();
        sb.append(getClass().isAnonymousClass() ? getClass().getName() : getClass().getSimpleName());
        sb.append('{');
        if(filter!=null) {
            sb.append("\n  filter=");
            sb.append(filter.toString(provider).replace("\n", "\n    "));
            sb.append('\n');
        }
        sb.append('}');
        return sb.toString();
    }
    
}
